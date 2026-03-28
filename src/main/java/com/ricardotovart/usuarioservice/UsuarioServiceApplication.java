package com.ricardotovart.usuarioservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UsuarioServiceApplication {

	public static void main(String[] args) {
		prueba();
		SpringApplication.run(UsuarioServiceApplication.class, args);
	}

	// String pool
	public static void prueba(){
		// El String pool es una zona especial dentro del heap de Java donde la JVM almacena strings para reutilizarlos
		// en lugar de crear nuevos objetos cada vez que se use un string
		// Esto mejora el rendimiento al evitar la creación de objetos innecesarios
		String str1 = "hola"; // crea un nuevo objeto en el string pool
		String str2 = "hola";// JVM encuentra el objeto existente en el string pool, lo reutiliza
		String str3 = new String("hola"); // crea un nuevo objeto en el heap
		String str4 = new String("hola"); // crea un nuevo objeto en el heap

		System.out.println(str1 == str2); // son iguales porque comparten la misma referencia
		System.out.println(str1 == str3); // no son iguales porque no comparten la misma referencia
		System.out.println(str1.equals(str3)); // son iguales porque tienen el mismo valor , usando equals()
		System.out.println(str3 == str4); // no son iguales porque no comparten la misma referencia
	}

}
