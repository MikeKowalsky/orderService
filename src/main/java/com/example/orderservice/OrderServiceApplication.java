package com.example.orderservice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.util.Date;

@SpringBootApplication
public class OrderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderServiceApplication.class, args);
	}

}


enum OrderEvents {
	FULFILL,
	PAY,
	CANCEL
}

enum OrderStates {
	SUBMITTED,
	PAID,
	FULFILLED,
	CANCELED
}

@Log
@Component
class Runner implements ApplicationRunner {

	private final OrderService orderService;

	Runner(OrderService orderService){
		this.orderService = orderService;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {

		Order order = this.orderService.create(new Date());

	}
}


interface OrderRepository extends JpaRepository<Order, Long>{}


@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
class Order {

	@Id
	@GeneratedValue
	private Long id;
	private Date datetime;
	private String state;

	public Order(Date d, OrderStates os){
		this.datetime = d;
		this.setOrderState(os);
	}

	public OrderStates getOrderState(){
		return OrderStates.valueOf(this.state);
	}

	public void setOrderState(OrderStates s){
		this.state = s.name();
	}
}

@Service
class OrderService {

	private final OrderRepository orderRepository;

	OrderService(OrderRepository orderRepository){
		this.orderRepository = orderRepository;
	}

	Order create(Date when){
		return this.orderRepository
				.save(new Order(when, OrderStates.SUBMITTED));
	}
}


@Log
@Configuration
@EnableStateMachineFactory
class SimpleEnumStatemachineConfiguration extends StateMachineConfigurerAdapter <OrderStates, OrderEvents> {

	@Override
	public void configure(StateMachineTransitionConfigurer<OrderStates, OrderEvents> transitions) throws Exception {
		transitions
			.withExternal().source(OrderStates.SUBMITTED).target(OrderStates.PAID).event(OrderEvents.PAY)
			.and()
			.withExternal().source(OrderStates.PAID).target(OrderStates.FULFILLED).event(OrderEvents.FULFILL)
			.and()
			.withExternal().source(OrderStates.SUBMITTED).target(OrderStates.CANCELED).event(OrderEvents.CANCEL)
			.and()
			.withExternal().source(OrderStates.PAID).target(OrderStates.CANCELED).event(OrderEvents.CANCEL);
	}

	@Override
	public void configure(StateMachineStateConfigurer<OrderStates, OrderEvents> states) throws Exception {
		states
			.withStates()
			.initial(OrderStates.SUBMITTED)
			.stateEntry(OrderStates.SUBMITTED, new Action<OrderStates, OrderEvents>() {
				@Override
				public void execute(StateContext<OrderStates, OrderEvents> stateContext) {
					Long orderId = Long.class.cast(stateContext.getExtendedState().getVariables().getOrDefault("orderId", -1L));
					log.info("OrderId is " + orderId + ".");
					log.info("-----> entering submitted state");
				}
			})
			.state(OrderStates.PAID)
			.end(OrderStates.FULFILLED)
			.end(OrderStates.CANCELED);
	}

	@Override
	public void configure(StateMachineConfigurationConfigurer<OrderStates, OrderEvents> config) throws Exception {
		StateMachineListenerAdapter<OrderStates, OrderEvents> adapter = new StateMachineListenerAdapter<OrderStates, OrderEvents>(){
			@Override
			public void stateChanged(State<OrderStates, OrderEvents> from, State<OrderStates, OrderEvents> to) {
				log.info(String.format("stateChanged(from: %s, to: %s)", from + "", to + ""));
			}
		};
		config.withConfiguration()
			.autoStartup(false)
			.listener(adapter);
	}
}
