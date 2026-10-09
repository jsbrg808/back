package ordinal.third.pogo.calcydex.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

@Component
public class CalcyDexHttpConnectionFactory {

    public HttpURLConnection open(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }
}
