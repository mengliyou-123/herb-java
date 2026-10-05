package org.herb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Hello world!
 *
 */
@SpringBootApplication
public class HerbApplication
{
    public static void main( String[] args )
    {
        org.herb.config.LocalEnvironment.load();
        SpringApplication.run(HerbApplication.class,args);
        //System.out.println( "Hello World!" );
    }
}
