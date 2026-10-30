package org.sjtu.se.se3353_22_grpcclient;

import org.sjtu.se.se3353_22_grpc.HelloRequest;
import org.sjtu.se.se3353_22_grpc.SimpleGrpc;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.grpc.client.GrpcChannelFactory;

@SpringBootApplication
public class Se335322GrpcclientApplication {

    public static void main(String[] args) {
        SpringApplication.run(Se335322GrpcclientApplication.class, args);
    }

    @Bean
    SimpleGrpc.SimpleBlockingStub stub(GrpcChannelFactory channels) {
        return SimpleGrpc.newBlockingStub(channels.createChannel("local"));
    }

    @Bean
    public CommandLineRunner runner(SimpleGrpc.SimpleBlockingStub stub) {
        return args -> {
            System.out.println(stub.sayHello(HelloRequest.newBuilder().setName("Alien").build()));
        };
    }}
