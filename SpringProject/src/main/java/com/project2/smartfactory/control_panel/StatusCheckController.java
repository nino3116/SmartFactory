package com.project2.smartfactory.control_panel;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/status")
public class StatusCheckController {
  @GetMapping("/image_stream")
  public ResponseEntity<String> ImageStreamStatus (){
    String[] targetURL = new String[2];
    targetURL[0] = "http://localhost:8080";
    targetURL[1] = "http://192.168.0.124:8000/stream.mjpg";

    for(int i=0; i<targetURL.length;i++){
      try{
        URL url = URI.create(targetURL[1]).toURL();
        System.out.println(url);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        System.out.println(connection);
        connection.setRequestMethod("HEAD");
        int responsecode = connection.getResponseCode();
        System.out.println(responsecode);
        if(responsecode == 501){
          return new ResponseEntity<String>("ALIVE", HttpStatus.OK);
        }else{
          continue;
        }
      }catch(Exception e){
        // e.printStackTrace();
        continue;
      }
    }
    return new ResponseEntity<String>("ERROR", HttpStatus.OK);
  }
}
