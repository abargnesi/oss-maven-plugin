package com.acme.project;

import org.apache.commons.lang3.ArrayUtils;

public class Main {

    public static void main(String[] args) {
        if (ArrayUtils.isEmpty(args)) {
            System.out.println("Arguments are empty.");
        } else {
            System.out.println("Arguments have some.");
        }
    }
}