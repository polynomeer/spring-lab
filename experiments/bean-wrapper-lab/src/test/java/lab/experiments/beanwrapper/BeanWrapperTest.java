package lab.experiments.beanwrapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.NullValueInNestedPathException;
import org.springframework.validation.DataBinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BeanWrapperTest {

    @Test
    void settingANestedPropertyThroughANullIntermediateThrowsByDefault() {
        BeanWrapper wrapper = new BeanWrapperImpl(new Person());

        assertThatThrownBy(() -> wrapper.setPropertyValue("address.city", "Seoul"))
                .isInstanceOf(NullValueInNestedPathException.class);
    }

    @Test
    void enablingAutoGrowNestedPathsAutoInstantiatesTheNullIntermediateObject() {
        BeanWrapper wrapper = new BeanWrapperImpl(new Person());
        wrapper.setAutoGrowNestedPaths(true);

        wrapper.setPropertyValue("address.city", "Seoul");

        Person person = (Person) wrapper.getWrappedInstance();
        assertThat(person.getAddress()).isNotNull();
        assertThat(person.getAddress().getCity()).isEqualTo("Seoul");
    }

    @Test
    void indexedListPropertyGrowsAutomaticallyOnSetEvenWithoutAutoGrowNestedPaths() {
        // autoGrowNestedPaths는 여전히 기본값(false)이다 - 그런데도 빈 리스트에
        // tags[2]를 설정하면 성공한다. 리스트/배열의 자동 확장은 autoGrowNestedPaths가
        // 아니라 완전히 별개의 스위치(autoGrowCollectionLimit, 기본값 Integer.MAX_VALUE)가
        // 결정하기 때문이다 - "중첩 경로의 null 객체를 자동 생성하는 것"과 "컬렉션을 자동
        // 확장하는 것"은 겉보기엔 둘 다 "자동 확장"처럼 보이지만 서로 다른 게이트다.
        BeanWrapper wrapper = new BeanWrapperImpl(new Person());

        wrapper.setPropertyValue("tags[2]", "third");

        Person person = (Person) wrapper.getWrappedInstance();
        assertThat(person.getTags()).containsExactly(null, null, "third");
    }

    @Test
    void loweringAutoGrowCollectionLimitBlocksThatSameListGrowth() {
        BeanWrapper wrapper = new BeanWrapperImpl(new Person());
        wrapper.setAutoGrowCollectionLimit(1);

        // index(2) >= autoGrowCollectionLimit(1)이므로 이번엔 확장하지 않고 바로
        // list.set(2, ...)를 시도한다 - 빈 리스트에는 인덱스 2가 없으므로 실패한다.
        assertThatThrownBy(() -> wrapper.setPropertyValue("tags[2]", "third"))
                .isInstanceOf(InvalidPropertyException.class);
    }

    @Test
    void mapPropertyAcceptsAnyKeyRegardlessOfAnyGrowSetting() {
        // Map은 "확장"이라는 개념 자체가 없다 - 어떤 키든 그냥 put()하면 된다. autoGrowNestedPaths도
        // autoGrowCollectionLimit도 전혀 관여하지 않는다.
        BeanWrapper wrapper = new BeanWrapperImpl(new Person());

        wrapper.setPropertyValue("attributes[color]", "blue");

        Person person = (Person) wrapper.getWrappedInstance();
        assertThat(person.getAttributes()).containsEntry("color", "blue");
    }

    @Test
    void dataBinderDefaultsAutoGrowNestedPathsToTrueUnlikePlainBeanWrapperImpl() {
        // DataBinder(Spring MVC의 @ModelAttribute/WebDataBinder가 쓰는 바로 그 클래스)는
        // autoGrowNestedPaths의 기본값을 true로 뒤집어 둔다 - BeanWrapperImpl을 직접 쓸 때와
        // 정반대의 기본 동작이다. 폼/요청 바인딩에서는 중첩 객체가 자동으로 만들어지는 게
        // 자연스럽지만, 순수 BeanWrapperImpl은 그런 가정을 하지 않는다는 뜻이다.
        Person person = new Person();
        DataBinder binder = new DataBinder(person);
        assertThat(binder.isAutoGrowNestedPaths()).isTrue();

        MutablePropertyValues pvs = new MutablePropertyValues();
        pvs.add("address.city", "Busan");
        binder.bind(pvs);

        assertThat(person.getAddress()).isNotNull();
        assertThat(person.getAddress().getCity()).isEqualTo("Busan");
    }
}
