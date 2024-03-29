package org.hibernate.search.test.bridge;

import org.apache.lucene.queryParser.QueryParser;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.search.FullTextQuery;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.hibernate.search.test.SearchTestCase;

import java.util.List;

/**
 * @author Emmanuel Bernard
 */
public class ClassBridgeAndProjectionTest  extends SearchTestCase {

    public void testClassBridgeProjection() throws Exception {
        Session s = openSession();
        Transaction tx = s.beginTransaction();

        // create entities
        Teacher teacher = new Teacher();
        teacher.setName("John Smith");
        s.persist(teacher);

        Student student1 = new Student();
        student1.setGrade("foo");
        student1.setName("Jack Miller");
        student1.setTeacher(teacher);
        teacher.getStudents().add(student1);
        s.persist(student1);

        Student student2 = new Student();
        student2.setGrade("bar");
        student2.setName("Steve Marshall");
        student2.setTeacher(teacher);
        teacher.getStudents().add(student2);
        s.persist(student2);

        tx.commit();

        // test query without projection
        FullTextSession ftSession = Search.getFullTextSession( s );
        QueryParser parser = new QueryParser(
        		getTargetLuceneVersion(),
                "name", 
                standardAnalyzer );
        FullTextQuery query = ftSession.createFullTextQuery(parser.parse("name:John"), Teacher.class);
        List results = query.list();
        assertNotNull(results);
        assertTrue(results.size() == 1);
        assertTrue(((Teacher) results.get(0)).getStudents().size() == 2);

        // now test with projection
        query.setProjection("amount_of_students");
        results = query.list();
        assertNotNull(results);
        assertTrue(results.size() == 1);
        Object[] firstResult = (Object[]) results.get(0);
        Integer amountStudents = (Integer) firstResult[0];
        assertEquals(new Integer(2), amountStudents);

        s.close();
    }

    @Override
    protected Class<?>[] getMappings() {
        return new Class<?>[] {
                Student.class,
                Teacher.class
        };
    }
}
