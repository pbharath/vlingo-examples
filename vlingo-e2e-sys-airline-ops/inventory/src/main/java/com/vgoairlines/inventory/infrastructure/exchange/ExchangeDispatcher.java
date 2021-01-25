package com.vgoairlines.inventory.infrastructure.exchange;

import io.vlingo.common.serialization.JsonSerialization;
import io.vlingo.lattice.exchange.Exchange;
import io.vlingo.symbio.Entry;
import io.vlingo.symbio.State;
import io.vlingo.symbio.store.Result;
import io.vlingo.symbio.store.dispatch.ConfirmDispatchedResultInterest;
import io.vlingo.symbio.store.dispatch.Dispatchable;
import io.vlingo.symbio.store.dispatch.Dispatcher;
import io.vlingo.symbio.store.dispatch.DispatcherControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.stream.Stream;
import java.util.Collections;
import java.util.stream.Collectors;

import com.vgoairlines.inventory.model.aircraft.AircraftConsigned;

public class ExchangeDispatcher implements Dispatcher<Dispatchable<Entry<String>, State<String>>>, ConfirmDispatchedResultInterest {
  private static final Logger logger = LoggerFactory.getLogger(ExchangeDispatcher.class);

  private DispatcherControl control;
  private final List<Exchange> producerExchanges;
  private final Map<String, Set<String>> eventsByExchangeName = new HashMap<>();

  public ExchangeDispatcher(final Exchange ...producerExchanges) {
    this.eventsByExchangeName.put("inventory-exchange", new HashSet<>());
    this.eventsByExchangeName.get("inventory-exchange").add(AircraftConsigned.class.getCanonicalName());
    this.producerExchanges = Arrays.asList(producerExchanges);
  }

  @Override
  public void dispatch(final Dispatchable<Entry<String>, State<String>> dispatchable) {
    logger.debug("Going to dispatch id {}", dispatchable.id());

    for (Entry<String> entry : dispatchable.entries()) {
      this.send(JsonSerialization.deserialized(entry.entryData(), entry.typed()));
    }

    this.control.confirmDispatched(dispatchable.id(), this);
  }

  @Override
  public void confirmDispatchedResultedIn(Result result, String dispatchId) {
      logger.debug("Dispatch id {} resulted in {}", dispatchId, result);
  }

  @Override
  public void controlWith(DispatcherControl control) {
    this.control = control;
  }

  private void send(final Object event) {
    this.findInterestedIn(event).forEach(exchange -> exchange.send(event));
  }

  private Stream<Exchange> findInterestedIn(final Object event) {
    final Set<String> exchangeNames =
          eventsByExchangeName.entrySet().stream().filter(exchange -> {
             final Set<String> events = exchange.getValue();
             return events.contains(event.getClass().getCanonicalName());
         }).map(Map.Entry::getKey).collect(Collectors.toSet());

    return this.producerExchanges.stream().filter(exchange -> exchangeNames.contains(exchange.name()));
  }

}