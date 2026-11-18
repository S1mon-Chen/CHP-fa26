package org.reins.se3353.se3353_10_functionsample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;

import java.util.function.Function;

@SpringBootApplication
public class Se335310FunctionsampleApplication {
	@Bean
	public Function<Flux<String>, Flux<String>> uppercase() {
		return flux -> flux.map(value -> value.toUpperCase());
	}

	@Bean
	public Function<Flux<Integer>, Flux<Integer>> name() {
		return e -> e.map(value -> value * 2) ;
	}

	@Bean
	public Function<Flux<Integer>, Flux<Integer>> square() {
		return e -> e.map(value -> value * value) ;
	}

	@Bean
	public Function<Flux<Integer>, Flux<Integer>> compose() {
		return e -> name().compose(square()).apply(e);
	}

	@Bean
	public Function<Flux<Integer>, Flux<Integer>> andThen() {
		return e -> name().andThen(square()).apply(e);
	}

	public static void main(String[] args) {
		SpringApplication.run(Se335310FunctionsampleApplication.class, args);
	}

}
