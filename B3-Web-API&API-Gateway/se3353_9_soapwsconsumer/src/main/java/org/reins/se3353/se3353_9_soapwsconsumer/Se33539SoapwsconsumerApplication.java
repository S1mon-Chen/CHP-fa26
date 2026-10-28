package org.reins.se3353.se3353_9_soapwsconsumer;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import org.reins.se3353.se3353_9_soapwsconsumer.wsdl.GetCountryResponse;

@SpringBootApplication
public class Se33539SoapwsconsumerApplication {

	public static void main(String[] args) {
		SpringApplication.run(Se33539SoapwsconsumerApplication.class, args);
	}

	@Bean
	CommandLineRunner lookup(CountryClient quoteClient) {
		return args -> {
			String country = "Spain";

			if (args.length > 0) {
				country = args[0];
			}
			GetCountryResponse response = quoteClient.getCountry(country);
			System.err.println(response.getCountry().getCurrency());
		};
	}

}
