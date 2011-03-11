/*
 * Copyright 2008-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.jpa.repository.support;

import static junit.framework.Assert.*;

import java.io.IOException;
import java.io.Serializable;

import javax.persistence.EntityManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.custom.CustomGenericJpaRepositoryFactory;
import org.springframework.data.jpa.repository.custom.UserCustomExtendedRepository;


/**
 * Unit test for {@code JpaRepositoryFactory}.
 * 
 * @author Oliver Gierke
 */
@RunWith(MockitoJUnitRunner.class)
public class JpaRepositoryFactoryUnitTests {

    JpaRepositoryFactory factory;

    @Mock
    EntityManager entityManager;
    @Mock
    JpaEntityInformation<Object, Serializable> metadata;


    @Before
    public void setUp() {

        // Setup standard factory configuration
        factory = new JpaRepositoryFactory(entityManager) {

            @Override
            @SuppressWarnings("unchecked")
            public <T, ID extends Serializable> JpaEntityInformation<T, ID> getEntityInformation(
                    Class<T> domainClass) {

                return (JpaEntityInformation<T, ID>) metadata;
            };
        };
    }


    /**
     * Assert that the instance created for the standard configuration is a
     * valid {@code UserRepository}.
     * 
     * @throws Exception
     */
    @Test
    public void setsUpBasicInstanceCorrectly() throws Exception {

        assertNotNull(factory.getRepository(SimpleSampleRepository.class));
    }


    @Test
    public void allowsCallingOfObjectMethods() {

        SimpleSampleRepository repository =
                factory.getRepository(SimpleSampleRepository.class);

        repository.hashCode();
        repository.toString();
        repository.equals(repository);
    }


    /**
     * Asserts that the factory recognized configured repository classes that
     * contain custom method but no custom implementation could be found.
     * Furthremore the exception has to contain the name of the repository
     * interface as for a large repository configuration it's hard to find out
     * where this error occured.
     * 
     * @throws Exception
     */
    @Test
    public void capturesMissingCustomImplementationAndProvidesInterfacename()
            throws Exception {

        try {
            factory.getRepository(SampleRepository.class);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage()
                    .contains(SampleRepository.class.getName()));
        }
    }


    @Test(expected = IllegalArgumentException.class)
    public void handlesRuntimeExceptionsCorrectly() {

        SampleRepository repository =
                factory.getRepository(SampleRepository.class,
                        new SampleCustomRepositoryImpl());
        repository.throwingRuntimeException();
    }


    @Test(expected = IOException.class)
    public void handlesCheckedExceptionsCorrectly() throws Exception {

        SampleRepository repository =
                factory.getRepository(SampleRepository.class,
                        new SampleCustomRepositoryImpl());
        repository.throwingCheckedException();
    }


    @Test(expected = UnsupportedOperationException.class)
    public void createsProxyWithCustomBaseClass() throws Exception {

        JpaRepositoryFactory factory =
                new CustomGenericJpaRepositoryFactory(entityManager);
        UserCustomExtendedRepository repository =
                factory.getRepository(UserCustomExtendedRepository.class);

        repository.customMethod(1);
    }

    private interface SimpleSampleRepository extends
            JpaRepository<User, Integer> {

    }

    /**
     * Sample interface to contain a custom method.
     * 
     * @author Oliver Gierke
     */
    public interface SampleCustomRepository {

        void throwingRuntimeException();


        void throwingCheckedException() throws IOException;
    }

    /**
     * Implementation of the custom repository interface.
     * 
     * @author Oliver Gierke
     */
    private class SampleCustomRepositoryImpl implements SampleCustomRepository {

        public void throwingRuntimeException() {

            throw new IllegalArgumentException("You lose!");
        }


        public void throwingCheckedException() throws IOException {

            throw new IOException("You lose!");
        }
    }

    private interface SampleRepository extends JpaRepository<User, Integer>,
            SampleCustomRepository {

    }

    /**
     * Helper class to make the factory use {@link PersistableMetadata} .
     * 
     * @author Oliver Gierke
     */
    @SuppressWarnings("serial")
    private static abstract class User implements Persistable<Long> {

    }
}
