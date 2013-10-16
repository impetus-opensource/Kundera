/*******************************************************************************
 * * Copyright 2012 Impetus Infotech.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 ******************************************************************************/
package com.impetus.client.oraclenosql;

import java.io.IOException;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.servlet.UnavailableException;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.impetus.client.oraclenosql.entities.PersonKVStore;
import com.impetus.kundera.metadata.model.KunderaMetadata;

/**
 * The Class EntityTransactionTest.
 * 
 * @author vivek.mishra
 */
public class OracleEntityTransactionTest
{

    /** The emf. */
    private static EntityManagerFactory emf;

    /** The em. */
    private static EntityManager em;

    /**
     * Sets the up.
     * 
     * @throws Exception
     *             the exception
     */
    @Before
    public void setUp() throws Exception
    {
        emf = Persistence.createEntityManagerFactory("twikvstore");
        em = emf.createEntityManager();
    }

    /**
     * On rollback.
     * 
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     * @throws TException
     *             the t exception
     * @throws InvalidRequestException
     *             the invalid request exception
     * @throws UnavailableException
     *             the unavailable exception
     * @throws TimedOutException
     *             the timed out exception
     * @throws SchemaDisagreementException
     *             the schema disagreement exception
     */
    @Test
    public void onRollback() throws IOException
    {

        em.getTransaction().begin();
        Object p1 = prepareData("1", 10);
        Object p2 = prepareData("2", 20);
        Object p3 = prepareData("3", 15);
        em.persist(p1);
        em.persist(p2);
        em.persist(p3);

        // roll back.
        em.getTransaction().rollback();

        em.getTransaction().begin();

        PersonKVStore p = findById(PersonKVStore.class, "1", em);
        Assert.assertNull(p);

        // on commit.
        em.getTransaction().commit();

        // Still no record should be flushed as already rollback!
        p = findById(PersonKVStore.class, "1", em);
        Assert.assertNull(p);
    }

    /**
     * On commit.
     * 
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     * @throws TException
     *             the t exception
     * @throws InvalidRequestException
     *             the invalid request exception
     * @throws UnavailableException
     *             the unavailable exception
     * @throws TimedOutException
     *             the timed out exception
     * @throws SchemaDisagreementException
     *             the schema disagreement exception
     */
    @Test
    public void onCommit() throws IOException
    {

        em.getTransaction().begin();

        Object p1 = prepareData("1", 10);
        Object p2 = prepareData("2", 20);
        Object p3 = prepareData("3", 15);
        em.persist(p1);
        em.persist(p2);
        em.persist(p3);

        // on commit.
        em.getTransaction().commit();

        PersonKVStore p = findById(PersonKVStore.class, "1", em);
        Assert.assertNotNull(p);

        em.getTransaction().begin();

        ((PersonKVStore) p2).setPersonName("rollback");
        em.merge(p2);

        // roll back, should roll back person name for p2!
        em.getTransaction().rollback();

        p = findById(PersonKVStore.class, "1", em);
        Assert.assertNotNull(p);

        p = findById(PersonKVStore.class, "2", em);
        Assert.assertNotNull(p);
        Assert.assertEquals("vivek", p.getPersonName());
        Assert.assertNotSame("rollback", p.getPersonName());

    }

    /**
     * Rollback on error.
     * 
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     * @throws TException
     *             the t exception
     * @throws InvalidRequestException
     *             the invalid request exception
     * @throws UnavailableException
     *             the unavailable exception
     * @throws TimedOutException
     *             the timed out exception
     * @throws SchemaDisagreementException
     *             the schema disagreement exception
     */
    @Test
    public void rollbackOnError() throws IOException
    {
        PersonKVStore p = null;
        try
        {
            Object p1 = prepareData("1", 10);
            Object p2 = prepareData("2", 20);
            em.persist(p1);
            em.persist(p2);

            p = findById(PersonKVStore.class, "1", em);
            Assert.assertNotNull(p);

            Object p3 = prepareData("3", 15);
            em.persist(p3);

            // Assert on rollback on error.
            ((PersonKVStore) p2).setPersonName("rollback");
            em.merge(p2);
            em.merge(null);

            // As this is a runtime exception so rollback should happen and
            // delete out commited data.
        }
        catch (Exception ex)
        {

            p = findById(PersonKVStore.class, "1", em);
            Assert.assertNull(p);

            p = findById(PersonKVStore.class, "2", em);
            Assert.assertNull(p);

            p = findById(PersonKVStore.class, "3", em);
            Assert.assertNull(p);
        }
        em.clear();
        // persist with 1 em
        EntityManager em1 = emf.createEntityManager();
        // em1.setFlushMode(FlushModeType.COMMIT);
        em1.getTransaction().begin();
        Object p3 = prepareData("4", 15);
        em1.persist(p3);
        em1.getTransaction().commit();

        try
        {
            // remove with another em with auto flush.
            EntityManager em2 = emf.createEntityManager();
            PersonKVStore person = em2.find(PersonKVStore.class, "4");
            em2.remove(person);
            em2.merge(null);
        }
        catch (Exception ex)
        {
            // Deleted records cannot be rolled back in cassandra!
            // em1.clear();

            p = findById(PersonKVStore.class, "4", em1);
            Assert.assertNotNull(p);
            Assert.assertEquals("vivek", p.getPersonName());

        }
    }

    /**
     * Roll back with multi transactions.
     */
    @Test
    public void rollBackWithMultiTransactions()
    {
        EntityManager em1 = emf.createEntityManager();
        // em1.setFlushMode(FlushModeType.COMMIT);

        // Begin transaction.
        em1.getTransaction().begin();
        Object p1 = prepareData("11", 10);
        em1.persist(p1);

        // commit p1.
        em1.getTransaction().commit();

        // another em instance
        EntityManager em2 = emf.createEntityManager();
        // em2.setFlushMode(FlushModeType.COMMIT);

        // begin transaction.
        em2.getTransaction().begin();
        PersonKVStore found = em2.find(PersonKVStore.class, "11");
        found.setPersonName("merged");
        em2.merge(found);

        // commit p1 after modification.
        em2.getTransaction().commit();

        // open another entity manager.
        EntityManager em3 = emf.createEntityManager();
        found = em3.find(PersonKVStore.class, "11");
        found.setPersonName("lastemerge");
        try
        {
            em3.merge(found);
            em3.merge(null);
        }
        catch (Exception ex)
        {
            PersonKVStore finalFound = em2.find(PersonKVStore.class, "11");
            Assert.assertNotNull(finalFound);
            Assert.assertEquals("merged", finalFound.getPersonName());
        }
    }

    /**
     * Tear down.
     * 
     * @throws Exception
     *             the exception
     */
    @After
    public void tearDown() throws Exception
    {
        // Delete by query.
        String deleteQuery = "Delete from PersonKVStore p";
        Query query = em.createQuery(deleteQuery);
        int updateCount = query.executeUpdate();
        em.close();
        emf.close();
        KunderaMetadata.INSTANCE.setApplicationMetadata(null);
    }

    /**
     * Prepare data.
     * 
     * @param rowKey
     *            the row key
     * @param age
     *            the age
     * @return the person
     */
    private PersonKVStore prepareData(String rowKey, int age)
    {
        PersonKVStore o = new PersonKVStore();
        o.setPersonId(rowKey);
        o.setPersonName("vivek");
        o.setAge(age);
        return o;
    }

    /**
     * Find by id.
     * 
     * @param <E>
     *            the element type
     * @param clazz
     *            the clazz
     * @param rowKey
     *            the row key
     * @param em
     *            the em
     * @return the e
     */
    private <E extends Object> E findById(Class<E> clazz, Object rowKey, EntityManager em)
    {
        return em.find(clazz, rowKey);
    }
}
