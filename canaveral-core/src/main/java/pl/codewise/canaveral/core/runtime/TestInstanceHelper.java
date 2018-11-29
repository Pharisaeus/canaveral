package pl.codewise.canaveral.core.runtime;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.commons.lang3.reflect.FieldUtils;
import pl.codewise.canaveral.core.bean.inject.InjectMock;
import pl.codewise.canaveral.core.bean.inject.InjectTestBean;

import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.reflect.FieldUtils.getFieldsListWithAnnotation;

public class TestInstanceHelper {

    private final RunnerCache cache;

    TestInstanceHelper(RunnerCache cache) {
        this.cache = cache;
    }

    public Object initializeTestInstance(Object testInstance) {
        injectMocks(testInstance);
        injectTestBeans(testInstance);
        injectApplicationBeans(testInstance);

        return testInstance;
    }

    private void injectMocks(Object testInstance) {
        getFieldsListWithAnnotation(testInstance.getClass(), InjectMock.class)
                .forEach(field -> {
                    InjectMock annotation = field.getAnnotation(InjectMock.class);
                    String mockRef = annotation.value();
                    Object mock;
                    Class<?> mockType = field.getType();
                    if (Strings.isNullOrEmpty(mockRef)) {
                        mock = cache.getMock(mockType);
                    } else {
                        mock = cache.getMock(mockRef);
                    }
                    Preconditions.checkNotNull(mock, "There is no mock for \"" + mockRef + "\":" + mockType +
                            " requested in " + testInstance.getClass().getCanonicalName());
                    try {
                        FieldUtils.writeField(field, testInstance, mock, true);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException("Could not inject mock \"" + mockRef + "\":" + mockType +
                                " into " + testInstance.getClass().getCanonicalName());
                    }
                });
    }

    private void injectTestBeans(Object testInstance) {
        List<Field> injectionPoints = getFieldsListWithAnnotation(testInstance.getClass(), InjectTestBean.class);
        if (!injectionPoints.isEmpty() && !cache.hasTestConfigurationProvider()) {
            List<String> requestedBeans = injectionPoints.stream()
                    .map(Field::getType)
                    .map(Class::getCanonicalName)
                    .collect(Collectors.toList());
            throw new IllegalStateException("Test " + testInstance.getClass().getCanonicalName() +
                    "requires test beans " + requestedBeans + " by @InjectTestBean " +
                    "but no test bean provider available. " +
                    "Remove those annotation or configure test context.");
        }
        injectBeans(testInstance, injectionPoints, cache::getTestBean);
    }

    private void injectApplicationBeans(Object testInstance) {
        if (cache.hasApplicationProvider()) {
            List<Field> injectionPoints = getFieldsListWithAnnotation(testInstance.getClass(), Inject.class);
            injectBeans(testInstance, injectionPoints, cache::getApplicationBean);

            cache.getApplicationProvider().inject(testInstance);
        }
    }

    private void injectBeans(Object testInstance,
            List<Field> injectionPoints,
            BiFunction<Class<?>, Set<Annotation>, Object> beanProvider) {

        injectionPoints.forEach(field -> {
            Class<?> beanType = field.getType();
            try {
                Set<Annotation> annotations = Arrays.stream(field.getAnnotations()).collect(toSet());
                Object bean = beanProvider.apply(beanType, annotations);
                Preconditions.checkNotNull(bean, "There is no bean for " + beanType +
                        " requested in " + testInstance.getClass().getCanonicalName());
                FieldUtils.writeField(field, testInstance, bean, true);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Could not inject bean " + beanType +
                        " into " + testInstance.getClass().getCanonicalName());
            }
        });
    }
}
    
