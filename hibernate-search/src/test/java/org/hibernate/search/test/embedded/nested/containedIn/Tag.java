package org.hibernate.search.test.embedded.nested.containedIn;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Version;

import org.hibernate.search.annotations.Boost;
import org.hibernate.search.annotations.ContainedIn;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Store;

/**
 * @author Emmanuel Bernard
 */
@Entity
public class Tag {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;

	@Version
	private Long version;

	@Column(nullable = false, length = 50)
	@Field(index = org.hibernate.search.annotations.Index.TOKENIZED, store = Store.NO)
	@Boost(1.5f)
	private String name;

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "tag")
	@ContainedIn
	private List<HelpItemTag> helpItems;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<HelpItemTag> getHelpItems() {
		if ( helpItems == null ) {
			helpItems = new ArrayList<HelpItemTag>();
		}
		return helpItems;
	}

	public void setHelpItems(List<HelpItemTag> helpItems) {
		this.helpItems = helpItems;
	}

}
