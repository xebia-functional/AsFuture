package main;

import sample.Example;

import java.util.concurrent.ExecutionException;

public class Main {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        Example example = new Example();
        example.myFunction(1)
                .thenAccept(System.out::println)
                .thenCompose(v -> example.myFunction(2))
                .thenAccept(System.out::println)
                .get();
    }
}
