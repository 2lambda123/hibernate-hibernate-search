/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.mapper.orm.coordination.outboxpolling.event.impl;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.search.engine.backend.orchestration.spi.SingletonTask;
import org.hibernate.search.engine.reporting.FailureHandler;
import org.hibernate.search.mapper.orm.automaticindexing.spi.AutomaticIndexingMappingContext;
import org.hibernate.search.mapper.orm.common.spi.TransactionHelper;
import org.hibernate.search.mapper.orm.coordination.outboxpolling.cluster.impl.AgentRepository;
import org.hibernate.search.mapper.orm.coordination.outboxpolling.cluster.impl.AgentRepositoryProvider;
import org.hibernate.search.mapper.orm.coordination.outboxpolling.logging.impl.Log;
import org.hibernate.search.util.common.impl.Closer;
import org.hibernate.search.util.common.logging.impl.LoggerFactory;

public final class OutboxPollingEventProcessor {

	private static final Log log = LoggerFactory.make( Log.class, MethodHandles.lookup() );

	private enum Status {
		STOPPED,
		STARTED
	}

	private final String name;
	private final AutomaticIndexingMappingContext mapping;
	private final int pollingInterval;
	private final int batchSize;
	private final Integer transactionTimeout;
	private final int retryAfter;

	private final AtomicReference<Status> status = new AtomicReference<>( Status.STOPPED );
	private final AgentRepositoryProvider agentRepositoryProvider;
	private final OutboxPollingEventProcessorClusterLink clusterLink;
	private final TransactionHelper transactionHelper;
	private final FailureHandler failureHandler;
	private final Worker worker;
	private final SingletonTask processingTask;

	public OutboxPollingEventProcessor(String name,
			AutomaticIndexingMappingContext mapping, ScheduledExecutorService executor,
			AgentRepositoryProvider agentRepositoryProvider,
			OutboxPollingEventProcessorClusterLink clusterLink,
			int pollingInterval, int batchSize, Integer transactionTimeout, int retryAfter) {
		this.name = name;
		this.mapping = mapping;
		this.pollingInterval = pollingInterval;
		this.batchSize = batchSize;
		this.transactionTimeout = transactionTimeout;
		this.retryAfter = retryAfter;
		this.agentRepositoryProvider = agentRepositoryProvider;
		this.clusterLink = clusterLink;

		transactionHelper = new TransactionHelper( mapping.sessionFactory() );
		failureHandler = mapping.failureHandler();
		this.worker = new Worker();
		processingTask = new SingletonTask(
				name,
				worker,
				new Scheduler( executor ),
				failureHandler
		);
	}

	public void start() {
		log.startingOutboxEventProcessor( name );
		status.set( Status.STARTED );
		processingTask.ensureScheduled();
	}

	public CompletableFuture<?> completion() {
		return processingTask.completion();
	}

	public CompletableFuture<?> preStop() {
		status.set( Status.STOPPED );
		return processingTask.completion();
	}

	public void stop() {
		log.stoppingOutboxEventProcessor( name );
		try ( Closer<RuntimeException> closer = new Closer<>() ) {
			closer.push( SingletonTask::stop, processingTask );
			closer.push( OutboxPollingEventProcessor::leaveCluster, this );
		}
	}

	private void leaveCluster() {
		try ( SessionImplementor session = (SessionImplementor) mapping.sessionFactory().openSession() ) {
			transactionHelper.begin( session, transactionTimeout );
			try {
				AgentRepository agentRepository = agentRepositoryProvider.create( session );
				clusterLink.leaveCluster( agentRepository );
				transactionHelper.commit( session );
			}
			catch (RuntimeException e) {
				transactionHelper.rollbackSafely( session, e );
			}
		}
	}

	private class Worker implements SingletonTask.Worker {

		private volatile AgentInstructions agentInstructions;
		private volatile boolean lastExecutionProcessedEvents;

		@Override
		public CompletableFuture<?> work() {
			lastExecutionProcessedEvents = false;

			if ( mapping.sessionFactory().isClosed() ) {
				// Work around HHH-14541, which is not currently fixed in ORM 5.4.
				// Even if a fix gets backported, the bug will still be present in older 5.4 versions,
				// so we'd better keep this workaround.
				log.sessionFactoryIsClosedOnOutboxProcessing();
				return CompletableFuture.completedFuture( null );
			}

			// Optimization: don't even try to open a transaction/session if we know it's not necessary.
			if ( agentInstructions != null && agentInstructions.isStillValid()
					&& !agentInstructions.eventFinder.isPresent() ) {
				// Processing is disabled for the time being.
				// We will try again later (complete() will be called, re-scheduling the polling for later).
				return CompletableFuture.completedFuture( null );
			}

			try ( SessionImplementor session = (SessionImplementor) mapping.sessionFactory().openSession() ) {
				final OutboxEventProcessingPlan eventProcessing = new OutboxEventProcessingPlan( mapping, session );
				transactionHelper.inTransaction( session, transactionTimeout, s -> {
					if ( agentInstructions == null || !agentInstructions.isStillValid() ) {
						AgentRepository agentRepository = agentRepositoryProvider.create( session );
						agentInstructions = clusterLink.pulse( agentRepository );
					}
					Optional<OutboxEventFinder> eventFinder = agentInstructions.eventFinder;
					if ( !eventFinder.isPresent() ) {
						// Processing is disabled for the time being.
						// Don't do anything, we'll try again later
						// (complete() will be called, re-scheduling the polling for later)
						return;
					}
					List<OutboxEvent> events = eventFinder.get().findOutboxEvents( session, batchSize );
					if ( events.isEmpty() ) {
						// Nothing to do, try again later (complete() will be called, re-scheduling the polling for later)
						return;
					}

					// There are events to process
					lastExecutionProcessedEvents = true;
					// Make sure we will process the next batch ASAP
					// Since we set lastExecutionProcessedEvents to true,
					// calling ensureScheduled() will lead to immediate re-execution right after we're done.
					// See the Scheduler class below.
					ensureScheduled();

					log.tracef( "Processing %d outbox events for '%s': '%s'", events.size(), name, events );

					// Process the events
					eventProcessing.processEvents( events );
				} );

				// Updating events involves database locks on a table that
				// can see heavily concurrent access (the outbox table),
				// so we do that in a separate transaction, one that is as short as possible.
				OutboxEventUpdater eventUpdater = new OutboxEventUpdater(
						failureHandler, eventProcessing, session, name, retryAfter );
				// We potentially perform this update in multiple transactions,
				// each loading as many events as possible using SKIP_LOCKED,
				// to only load events that are not already locked by another processor.
				// This is to avoid problems related to lock escalation in MS SQL for example,
				// where another processor could be locking on our own events because
				// it locked a page instead of just a row.
				// For more information, see
				// org.hibernate.search.mapper.orm.coordination.outboxpolling.impl.OutboxEventLoader.tryLoadLocking
				while ( eventUpdater.thereAreStillEventsToProcess() ) {
					transactionHelper.inTransaction( session, transactionTimeout, s -> eventUpdater.process() );
				}

				return CompletableFuture.completedFuture( null );
			}
		}

		@Override
		public void complete() {
			// Make sure we poll again in a few seconds.
			// Since the worker is no longer working at this point,
			// calling ensureScheduled() will lead to delayed re-execution.
			ensureScheduled();
		}

		private void ensureScheduled() {
			// Only schedule the task while the Hibernate Search is started;
			// as soon as Hibernate Search stops,
			// we will finish processing the current batch of events and leave
			// the remaining events to be processed when the application restarts.
			if ( status.get() == Status.STARTED ) {
				processingTask.ensureScheduled();
			}
		}
	}

	private class Scheduler implements SingletonTask.Scheduler {
		private final ScheduledExecutorService delegate;

		private Scheduler(ScheduledExecutorService delegate) {
			this.delegate = delegate;
		}

		@Override
		public Future<?> schedule(Runnable runnable) {
			AgentInstructions instructions = worker.agentInstructions;
			if ( instructions == null ) {
				// Before the first pulse (i.e. on startup),
				// execute the worker only after the polling interval.
				// This is to mitigate the impact of infinite loops when there is an unhandled
				// failure while getting instructions (e.g. if the database is not up and running)
				// TODO Ideally we should record unhandled failures in a variable and force a wait after they happen.
				return delegate.schedule( runnable, pollingInterval, TimeUnit.MILLISECONDS );
			}
			else if ( instructions.eventFinder.isPresent() ) {
				if ( worker.lastExecutionProcessedEvents ) {
					// When running and there might be  more events to process,
					// re-execute the worker immediately.
					return delegate.submit( runnable );
				}
				else {
					// When running and there are no more events to process,
					// re-execute the worker after the polling interval.
					return delegate.schedule( runnable, pollingInterval, TimeUnit.MILLISECONDS );
				}
			}
			else {
				// On rebalancing or when suspended,
				// re-execute the worker only after the current instructions expire.
				// Instructions are expected to provide a reasonably high waiting time
				// between when they are issued and when they expire,
				// i.e. at least the polling interval,
				// to avoid polling the database continuously.
				return delegate.schedule( runnable, instructions.timeInMillisecondsToExpiration(), TimeUnit.MILLISECONDS );
			}
		}
	}
}