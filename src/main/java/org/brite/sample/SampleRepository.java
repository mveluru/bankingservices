package org.brite.sample;

import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

@Repository
public class SampleRepository {

    public static Map<String, Integer> map = new HashMap<>();

    public  SampleRepository() {
        map.put("Mac",2);
        map.put("Dell",3);
        map.put("IBM",4);

    }
    public Integer findByItem(String item) {
        return map.get(item);
    }
}
