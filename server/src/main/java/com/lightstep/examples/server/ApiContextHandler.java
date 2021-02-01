package com.lightstep.examples.server;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class ApiContextHandler extends ServletContextHandler
{
  // name your tracer after the class it instruments
  // spans started with this tracer will then 
  // be attributed to this package
  private static final Tracer tracer =
      GlobalOpenTelemetry.getTracer("com.lightstep.examples.server.ApiContextHandler");

  public ApiContextHandler()
  {
    addServlet(new ServletHolder(new ApiServlet()), "/hello");
  }

  // HttpServlet is automatically instrumented by OpenTelemetry
  static final class ApiServlet extends HttpServlet
  {
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException
    {
      // The current span has automatically been created by 
      // the servlet instrumentation.
      Span span = Span.current();
      
      // Set attributes to describe your operation. Attributes 
      // also function as indexes for finding your spans later. 
      // In this case, we're adding a route name, which is a common attribute.
      // These common attributes are defined using semantic conventions. Use these
      // conventions whenever they are available, as it helps backends recognize 
      // the meaning of the data.
      // https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/semantic_conventions/http.md#http-server-semantic-conventions
      span.setAttribute("http.route", "hello");

      // pretend to do work
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
      }

      // Start a child span to define a new operation. You don't need to make a 
      // lot of tiny child spans. A good rule of thumb is one for the controller, 
      // one for the model, one for the database call, one for rendering, etc.
      Span childSpan = tracer.spanBuilder("my-server-span").startSpan();
      
      // Once you've created a child span, you can set it as the current span by 
      // creating a closure (called a Scope in OpenTelemetry).
      // This is a best practice: you always want the span to wrap the operation it
      // is observing, and be available as the current span. Make sure to end the 
      // span in the finally block.
      // 
      // This is clearly a lot of code. I recommend making your framework manage 
      // spans for you, rather than adding spans directly in your application code.
      // You can also use the @WithSpan annotation to easily wrap a method in a span.
      try (Scope scope = childSpan.makeCurrent()) {
        
        // Inside the new scope, getCurrentSpan now returns the childSpan.
        // Also note that span methods can be chained.
        Span.current().setAttribute("ProjectId", "456")
                      .setAttribute("AccountId", "abcd");
          

        // Exceptions are important for observing your system.
        // The recordException method automatically formats an exception and 
        // records it as an event on the span. See more about events below.
        Span.current().recordException(new RuntimeException("oops"));
        
        // Not all exceptions are errors that fail the entire operation. For 
        // example, it might be normal in some cases to try to open a file that does not exist. 
        // To make an exception to count as an error, set the status on the span to Error.
        // This will cause the span to create alerts and count against your error budget.
        Span.current().setStatus(StatusCode.ERROR);
        
        // Pretend to do work
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        
        try (PrintWriter writer = res.getWriter()) {
          
          // Spans include events, which are structured logs. This is better than 
          // regular logging, as the events are included as part of the trace, 
          // allowing you to easily find all of the logs in a transaction.
          Span.current().addEvent("writing response",
            Attributes.of(AttributeKey.stringKey("content"), "hello world"));
          
          writer.write("Hello World");
        }

      } finally {
        // Always make sure to end spans that you create, or you will have a leak.
        childSpan.end();
      }
    }
  }
}
