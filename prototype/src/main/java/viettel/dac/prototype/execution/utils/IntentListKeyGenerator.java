package viettel.dac.prototype.execution.utils;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class IntentListKeyGenerator implements KeyGenerator {
    @Override
    public Object generate(Object target, Method method, Object... params) {
        if (params.length > 0 && params[0] instanceof List) {
            List<?> intents = (List<?>) params[0];
            return intents.stream()
                    .map(intent -> {
                        try {
                            Method getIntentMethod = intent.getClass().getMethod("getIntent");
                            return getIntentMethod.invoke(intent).toString();
                        } catch (Exception e) {
                            return intent.toString();
                        }
                    })
                    .collect(Collectors.joining(","));
        }
        return "";
    }
}
