package com.isuwang.dapeng.message.consumer.container;

import com.isuwang.dapeng.core.SoaBaseProcessor;
import com.isuwang.dapeng.core.SoaProcessFunction;
import com.isuwang.dapeng.core.TBeanSerializer;
import com.isuwang.dapeng.message.consumer.api.context.ConsumerContext;
import com.isuwang.dapeng.message.consumer.api.service.MessageConsumerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * Created by tangliu on 2016/9/18.
 */
public class KafkaMessageContainer {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaMessageContainer.class);

    public static Map<Object, Class<?>> contexts;

    @SuppressWarnings("unchecked")
    public void start() {

        MessageConsumerService consumerService = new com.isuwang.dapeng.message.consumer.kafka.MessageConsumerServiceImpl();

        Set<Object> ctxs = contexts.keySet();
        for (Object ctx : ctxs) {
            Class<?> contextClass = contexts.get(ctx);
            try {
                Method getBeansOfType = contextClass.getMethod("getBeansOfType", Class.class);
                Map<String, SoaBaseProcessor<?>> processorMap = (Map<String, SoaBaseProcessor<?>>) getBeansOfType.invoke(ctx, contextClass.getClassLoader().loadClass(SoaBaseProcessor.class.getName()));

                Set<String> keys = processorMap.keySet();
                for (String key : keys) {
                    SoaBaseProcessor<?> processor = processorMap.get(key);

                    long count = new ArrayList<>(Arrays.asList(processor.getIface().getClass().getInterfaces()))
                            .stream()
                            .filter(m -> m.getName().equals("org.springframework.aop.framework.Advised"))
                            .count();

                    Class<?> ifaceClass = (Class) (count > 0 ? processor.getIface().getClass().getMethod("getTargetClass").invoke(processor.getIface()) : processor.getIface().getClass());

                    Class MessageConsumerClass = null;
                    Class MessageConsumerActionClass = null;
                    try {
                        MessageConsumerClass = ifaceClass.getClassLoader().loadClass("com.isuwang.dapeng.message.consumer.api.annotation.MessageConsumer");
                        MessageConsumerActionClass = ifaceClass.getClassLoader().loadClass("com.isuwang.dapeng.message.consumer.api.annotation.MessageConsumerAction");
                    } catch (ClassNotFoundException e) {
                        break;
                    }

                    if (ifaceClass.isAnnotationPresent(MessageConsumerClass)) {

                        Annotation messageConsumer = ifaceClass.getAnnotation(MessageConsumerClass);
                        String groupId = (String) messageConsumer.getClass().getDeclaredMethod("groupId").invoke(messageConsumer);

                        for (Method method : ifaceClass.getMethods()) {
                            if (method.isAnnotationPresent(MessageConsumerActionClass)) {

                                String methodName = method.getName();
                                SoaProcessFunction<Object, Object, Object, ? extends TBeanSerializer<Object>, ? extends TBeanSerializer<Object>> soaProcessFunction = (SoaProcessFunction<Object, Object, Object, ? extends TBeanSerializer<Object>, ? extends TBeanSerializer<Object>>) processor.getProcessMapView().get(methodName);

                                Annotation annotation = method.getAnnotation(MessageConsumerActionClass);
                                String topic = (String) annotation.getClass().getDeclaredMethod("topic").invoke(annotation);

                                ConsumerContext consumerContext = new ConsumerContext();
                                consumerContext.setGroupId(groupId);
                                consumerContext.setTopic(topic);
                                consumerContext.setIface(processor.getIface());
                                consumerContext.setSoaProcessFunction(soaProcessFunction);

                                consumerService.addConsumer(consumerContext);

                                LOGGER.info("添加消息订阅({})({})", ifaceClass.getName(), method.getName());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }
}
