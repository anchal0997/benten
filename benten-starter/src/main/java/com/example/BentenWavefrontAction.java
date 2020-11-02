package com.example;

import com.intuit.benten.common.actionhandlers.BentenActionHandler;
import com.intuit.benten.common.actionhandlers.BentenHandlerResponse;
import com.intuit.benten.common.actionhandlers.BentenSlackResponse;
import com.intuit.benten.common.annotations.ActionHandler;
import com.intuit.benten.common.formatters.SlackFormatter;
import com.intuit.benten.common.helpers.BentenMessageHelper;
import com.intuit.benten.common.http.HttpHelper;
import com.intuit.benten.common.nlp.BentenMessage;
import com.google.gson.JsonElement;
import org.apache.http.HttpResponse;
import java.time.Instant;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import java.io.IOException;
import net.minidev.json.parser.ParseException;
import java.lang.InterruptedException;



@Component
@ActionHandler(action = "action_benten_wavefront")
public class BentenWavefrontAction implements BentenActionHandler {
    @Autowired
    HttpHelper httpHelper;

    @Override
    public BentenHandlerResponse handle(BentenMessage bentenMessage) {
        String noOfPings = BentenMessageHelper.getParameterAsString(bentenMessage, "service-name");
        BentenHandlerResponse bentenHandlerResponse = new BentenHandlerResponse();
        BentenSlackResponse bentenSlackResponse = new BentenSlackResponse();
        bentenHandlerResponse.setBentenSlackResponse(bentenSlackResponse);
        String application = BentenMessageHelper.getParameterAsString(bentenMessage, "application");


        try {

            //taking 5 minutes less then epoch time
            long millis = 5 * 60 * 1000;

            String currentTimestamp = String.valueOf(Instant.now().toEpochMilli()-millis);

            Map<String, String> params = new HashMap();
            //, -- %2C
            // space %20
            // = -- %3D
            //  " -- %22
            String s1="round(.005,ts(iks.namespace.pod.http.server.requests.latency.quantiles, app=\""+noOfPings+"\" and namespace=\"identity-authz-rpas-use2-ppd-prf\" and quantile=\"0.98\")*1000)";
            s1=s1.replaceAll(",","%2C");
            s1=s1.replaceAll(" ","%20");
            s1=s1.replaceAll("=","%3D");
            s1=s1.replaceAll("\"","%22");


            params.put("q",s1);
            params.put("s",currentTimestamp);
            params.put("g","d");
            params.put("strict","false");
            params.put("sorted","true");
            params.put("view","METRIC");
            params.put("cached","true");
            params.put("useRawQK","false");

            String url="https://intuit.wavefront.com/api/v2/chart/api?";
            for (Map.Entry param : params.entrySet()) {
                System.out.println("Key: "+param.getKey() + " & Value: " + param.getValue());
                url=url+param.getKey()+"="+param.getValue()+"&";
            }

            if ((url != null) && (url.length() > 0)) {
                url = url.substring(0, url.length() - 1);
            }

            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeader("Authorization","Bearer c30c9ca6-e373-414d-ae1a-929024cbd79f");
//            httpGet.setParams(params);


            HttpResponse httpResponse = httpHelper.getClient().execute(httpGet);
//                String response12 = Integer.toString(httpResponse.getStatusLine().getStatusCode());


            String response12=EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(response12.toString());

            JSONArray jsonArray = (JSONArray) json.get("timeseries");
            Iterator<Object> iterator = jsonArray.iterator();
            String response="";
            while(iterator.hasNext()) {
                Object o = iterator.next();
                JSONObject j = (JSONObject) parser.parse(o.toString());
                response=(j.getAsString("data"));
            }
            response=response.replaceAll("\\[","");
            response=response.replaceAll("\\]","");
            String[] arrOfStr = response.split(",");
            response="";
            for (int i=0;i<arrOfStr.length;i=i+2)
            {
                response+="epoch time = "+arrOfStr[i]+", p98 = "+arrOfStr[i+1]+"\n";
            }


//            Thread.sleep(1000);

            String responseToSend = SlackFormatter.create().text("Response is : ")
                    .newline().preformatted(response)
                    .build();

            bentenSlackResponse.setSlackText(responseToSend);
            bentenMessage.getChannel().sendMessage(bentenHandlerResponse,bentenMessage.getChannelInformation());
            bentenSlackResponse.setSlackText("DONE");

        }
        catch(IOException e) {
            e.printStackTrace();
            String responseToSend = SlackFormatter.create().text("Sorry couldn't send the response. Check the service name ")
                    .build();

            bentenSlackResponse.setSlackText(responseToSend);
            bentenMessage.getChannel().sendMessage(bentenHandlerResponse,bentenMessage.getChannelInformation());
            bentenSlackResponse.setSlackText("DONE");
        }
        catch (ParseException e) {
            e.printStackTrace();
            String responseToSend = SlackFormatter.create().text("Sorry couldn't send the response. Check the service name ")
                    .build();

            bentenSlackResponse.setSlackText(responseToSend);
            bentenMessage.getChannel().sendMessage(bentenHandlerResponse,bentenMessage.getChannelInformation());
            bentenSlackResponse.setSlackText("DONE");
        }
//        catch(InterruptedException e) {
//            e.printStackTrace();
//        }
        catch (Exception ex) {
//            throw ex;
            String responseToSend = SlackFormatter.create().text("Sorry couldn't send the response. Check the service name ")
                    .build();

            bentenSlackResponse.setSlackText(responseToSend);
            bentenMessage.getChannel().sendMessage(bentenHandlerResponse,bentenMessage.getChannelInformation());
            bentenSlackResponse.setSlackText("DONE");
        }

        return bentenHandlerResponse;


    }
}
