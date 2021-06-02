package com.github.dskprt.javacrypt.test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;

public class Test1 {

    public static void main(String[] args) {
        System.out.println("Hello world!");

        URL url = Test1.class.getResource("test.txt");
        InputStream in = null;

        try {
            in = url.openConnection().getInputStream();
        } catch(IOException e) {
            e.printStackTrace();
        }

        if(in == null) {
            System.out.println("INPUT IS NULL");
        }

        Scanner s = new Scanner(in);

        while(s.hasNextLine()) {
            System.out.println(s.nextLine());
        }
    }
}
