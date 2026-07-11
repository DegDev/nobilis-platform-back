/*
 * Copyright (c) 2026 Dmitri Puscas (DegDev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.degdev.engine.common.bus.kafka;

import io.github.degdev.engine.common.bus.EventBus;
import io.github.degdev.engine.common.bus.EventHandler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * Mounts the Kafka adapter behind {@link EventBus}/{@link EventHandler}, opt-in on {@code
 * nobilis.integration.bus=kafka}. A host that doesn't select Kafka gets no bus beans — never a
 * fail-fast boot — mirroring {@code CryptoAutoConfiguration}. Registered from {@code
 * META-INF/spring/…AutoConfiguration.imports}: a host opts in by depending on {@code common} and
 * setting the property, not by component-scanning {@code io.github.degdev.engine.common}.
 *
 * <p>Producer and consumer factories are built directly from {@link KafkaEventBusProperties} rather
 * than relying on Spring Boot's own classpath-triggered {@code KafkaAutoConfiguration} — that keeps
 * adapter selection entirely under this gate instead of two independent autoconfiguration paths.
 * Consumption is wired via a raw {@link ConcurrentMessageListenerContainer} (not
 * {@code @KafkaListener}) so the topic set is computed at runtime from every registered {@link
 * EventHandler} bean's {@link EventHandler#topic()} — the listener container is created only when
 * at least one handler exists.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "nobilis.integration", name = "bus", havingValue = "kafka")
@EnableConfigurationProperties(KafkaEventBusProperties.class)
public class KafkaEventBusAutoConfiguration {

  @Bean
  public ProducerFactory<String, String> eventBusProducerFactory(KafkaEventBusProperties props) {
    Map<String, Object> configs = new HashMap<>();
    configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, props.bootstrapServers());
    configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    return new DefaultKafkaProducerFactory<>(configs);
  }

  @Bean
  public KafkaTemplate<String, String> eventBusKafkaTemplate(
      ProducerFactory<String, String> eventBusProducerFactory) {
    return new KafkaTemplate<>(eventBusProducerFactory);
  }

  @Bean
  public EventBus eventBus(KafkaTemplate<String, String> eventBusKafkaTemplate) {
    return new KafkaEventBus(eventBusKafkaTemplate);
  }

  @Bean
  public ConsumerFactory<String, String> eventBusConsumerFactory(KafkaEventBusProperties props) {
    Map<String, Object> configs = new HashMap<>();
    configs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, props.bootstrapServers());
    configs.put(ConsumerConfig.GROUP_ID_CONFIG, props.groupId());
    configs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    configs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    return new DefaultKafkaConsumerFactory<>(configs);
  }

  @Bean
  @ConditionalOnBean(EventHandler.class)
  public MessageListenerContainer eventBusListenerContainer(
      ConsumerFactory<String, String> eventBusConsumerFactory, List<EventHandler> handlers) {
    Map<String, List<EventHandler>> handlersByTopic =
        handlers.stream().collect(Collectors.groupingBy(EventHandler::topic));

    ContainerProperties containerProperties =
        new ContainerProperties(handlersByTopic.keySet().toArray(new String[0]));
    containerProperties.setMessageListener(
        (MessageListener<String, String>)
            record ->
                handlersByTopic
                    .getOrDefault(record.topic(), List.of())
                    .forEach(handler -> handler.handle(record.value())));

    return new ConcurrentMessageListenerContainer<>(eventBusConsumerFactory, containerProperties);
  }
}
