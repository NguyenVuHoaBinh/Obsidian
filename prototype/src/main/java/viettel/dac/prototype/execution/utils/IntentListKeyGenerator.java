package viettel.dac.prototype.execution.utils;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;
import viettel.dac.prototype.execution.model.Intent;

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
                            if (intent instanceof Intent) {
                                Intent i = (Intent) intent;
                                return i.getIntent() + "#" + i.getIntentId();
                            } else {
                                // Fallback for non-Intent objects
                                Method getIntentMethod = intent.getClass().getMethod("getIntent");
                                Method getIntentIdMethod = intent.getClass().getMethod("getIntentId");
                                return getIntentMethod.invoke(intent) + "#" + getIntentIdMethod.invoke(intent);
                            }
                        } catch (Exception e) {
                            return intent.toString();
                        }
                    })
                    .collect(Collectors.joining(","));
        }
        return "";
    }
}