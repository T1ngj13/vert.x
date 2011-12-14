package org.vertx.java.core.sockjs;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.logging.Logger;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class BaseTransport {

  private static final Logger log = Logger.getLogger(BaseTransport.class);

  protected final Map<String, Session> sessions;
  protected AppConfig config;

  protected static final String COMMON_PATH_ELEMENT_RE = "\\/[^\\/\\.]+\\/([^\\/\\.]+)\\/";

  public BaseTransport(Map<String, Session> sessions, AppConfig config) {
    this.sessions = sessions;
    this.config = config;
  }

  protected String[] parseMessageString(String msgs) {
    //Sock-JS client will only ever send Strings in a JSON array or as a JSON string so we can do some cheap parsing
    //without having to use a JSON lib
    String[] parts;
    if (!msgs.startsWith("\"")) {
      String[] split = msgs.split("\"");
      parts = new String[(split.length - 1) / 2];
      for (int i = 1; i < split.length - 1; i += 2) {
        parts[(i - 1) / 2] = split[i];
      }
    } else {
      String msg = msgs.substring(1, msgs.length() - 1);
      parts = new String[] {msg};
    }
    return parts;
  }

  protected Session getSession(final long timeout, final long heartbeatPeriod, final String sessionID,
                               Handler<SockJSSocket> sockHandler) {
    Session session = sessions.get(sessionID);
    if (session == null) {
      session = new Session(timeout, heartbeatPeriod, sockHandler, new SimpleHandler() {
        public void handle() {
          sessions.remove(sessionID);
        }
      });
      sessions.put(sessionID, session);
    }
    return session;
  }

  protected boolean checkJSON(String str, HttpServerResponse response) {
    if (!(str.startsWith("[\"") && str.endsWith("\"]"))) {
      //Invalid
      response.statusCode = 500;
      response.end("Broken JSON encoding.");
      return false;
    } else {
      return true;
    }
  }

  static void setJSESSIONID(AppConfig config, HttpServerRequest req) {
    String cookies = req.getHeader("Cookie");
    if (config.isInsertJSESSIONID()) {
      //Preserve existing JSESSIONID, if any
      if (cookies != null) {
        String[] parts;
        if (cookies.contains(";")) {
          parts = cookies.split(";");
        } else {
          parts = new String[] {cookies};
        }
        for (String part: parts) {
          if (part.startsWith("JSESSIONID")) {
            cookies = part + "; path=/";
            break;
          }
        }
      }
      if (cookies == null) {
        cookies = "JSESSIONID=dummy; path=/";
      }
      req.response.putHeader("Set-Cookie", cookies);
    }
  }

  static void setCORS(HttpServerResponse resp, String origin) {
    resp.putHeader("Access-Control-Allow-Origin", origin);
    resp.putHeader("Access-Control-Allow-Credentials", "true");
  }

  static Handler<HttpServerRequest> createCORSOptionsHandler(final AppConfig config, final String methods) {
    return new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        req.response.putHeader("Cache-Control", "public,max-age=31536000");
        long oneYearSeconds = 365 * 24 * 60 * 60;
        long oneYearms = oneYearSeconds * 1000;
        String expires = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz").format(new Date(System.currentTimeMillis() + oneYearms));
        req.response.putHeader("Expires", expires);
        req.response.putHeader("Allow", methods);
        req.response.putHeader("Access-Control-Max-Age", String.valueOf(oneYearSeconds));
        String origin = req.getHeader("Origin");
        if (origin == null) {
          origin = "*";
        }
        setCORS(req.response, origin);
        setJSESSIONID(config, req);
        req.response.statusCode = 204;
        req.response.end();
      }
    };
  }
}