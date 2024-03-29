/* $Id$
 * 
 * Hibernate, Relational Persistence for Idiomatic Java
 * 
 * Copyright (c) 2009, Red Hat, Inc. and/or its affiliates or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat, Inc.
 * 
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.search.backend.impl.lucene.works;

import org.apache.lucene.index.IndexWriter;
import org.hibernate.search.backend.LuceneWork;
import org.hibernate.search.batchindexing.MassIndexerProgressMonitor;

/**
 * @author Sanne Grinovero
 */
public interface LuceneWorkDelegate {
	
	/**
	 * Will perform work on an IndexWriter.
	 * @param work the LuceneWork to apply to the IndexWriter.
	 * @param writer the IndexWriter to use.
	 * @throws UnsupportedOperationException when the work is not compatible with an IndexWriter.
	 */
	void performWork(LuceneWork work, IndexWriter writer);
	
	/**
	 * Used for stats and performance counters, use the monitor
	 * to keep track of activity done on the index.
	 * @param work the work which was done.
	 * @param monitor the monitor tracking activity.
	 */
	void logWorkDone(LuceneWork work, MassIndexerProgressMonitor monitor);
	
}
