package ordinal.third.pogo.pokedex.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

@Component
public class PokedexHttpConnectionFactory {

    public HttpURLConnection open(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }
}
