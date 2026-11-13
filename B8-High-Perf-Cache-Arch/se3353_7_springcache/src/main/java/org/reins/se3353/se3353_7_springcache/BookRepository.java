package org.reins.se3353.se3353_7_springcache;

public interface BookRepository {

	Book getByIsbn(String isbn);

}
