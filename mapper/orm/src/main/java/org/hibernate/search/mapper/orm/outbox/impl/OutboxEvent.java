/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.mapper.orm.outbox.impl;

import java.util.Arrays;
import java.util.Objects;
import javax.persistence.Transient;

import org.hibernate.search.mapper.pojo.route.DocumentRoutesDescriptor;
import org.hibernate.search.util.common.serialization.spi.SerializationUtils;

public final class OutboxEvent implements OutboxEventBase {

	private Integer id;
	private Type type;

	private String entityName;
	private String entityId;
	private byte[] documentRoutes;
	@Transient
	private Object originalEntityId;

	public OutboxEvent() {
	}

	public OutboxEvent(
			Type type, String entityName, String entityId, DocumentRoutesDescriptor documentRoutesDescriptor,
			Object originalEntityId) {
		this.type = type;
		this.entityName = entityName;
		this.entityId = entityId;
		this.documentRoutes = SerializationUtils.serialize( documentRoutesDescriptor );
		this.originalEntityId = originalEntityId;
	}

	@Override
	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	@Override
	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	@Override
	public String getEntityName() {
		return entityName;
	}

	public void setEntityName(String entityName) {
		this.entityName = entityName;
	}

	@Override
	public String getEntityId() {
		return entityId;
	}

	public void setEntityId(String entityId) {
		this.entityId = entityId;
	}

	@Override
	public byte[] getDocumentRoutes() {
		return documentRoutes;
	}

	public void setDocumentRoutes(byte[] documentRoutes) {
		this.documentRoutes = documentRoutes;
	}

	public Object getOriginalEntityId() {
		return originalEntityId;
	}

	public void setOriginalEntityId(Object originalEntityId) {
		this.originalEntityId = originalEntityId;
	}

	@Override
	public boolean equals(Object o) {
		if ( this == o ) {
			return true;
		}
		if ( o == null || getClass() != o.getClass() ) {
			return false;
		}
		OutboxEvent event = (OutboxEvent) o;
		return type == event.type && Objects.equals( entityName, event.entityName ) && Objects.equals(
				entityId, event.entityId ) && Arrays.equals( documentRoutes, event.documentRoutes );
	}

	@Override
	public int hashCode() {
		int result = Objects.hash( type, entityName, entityId );
		result = 31 * result + Arrays.hashCode( documentRoutes );
		return result;
	}

	@Override
	public String toString() {
		return "OutboxEvent{" +
				"type=" + type +
				", entityName='" + entityName + '\'' +
				", entityId='" + entityId + '\'' +
				'}';
	}
}