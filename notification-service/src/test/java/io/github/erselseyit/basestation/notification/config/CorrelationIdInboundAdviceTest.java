package io.github.erselseyit.basestation.notification.config;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The inbound advice must make the correlation id visible to the listener's
 * logging and then remove it, so ids do not bleed between messages handled on
 * the same reused consumer thread.
 */
class CorrelationIdInboundAdviceTest {

    private static final String KEY = "correlationId";
    private final CorrelationIdInboundAdvice advice = new CorrelationIdInboundAdvice();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private Message messageWithHeader(String correlationId) {
        MessageProperties props = new MessageProperties();
        if (correlationId != null) {
            props.setHeader("X-Correlation-ID", correlationId);
        }
        return new Message(new byte[0], props);
    }

    private Message messageWithNativeCorrelationId(String correlationId) {
        MessageProperties props = new MessageProperties();
        props.setCorrelationId(correlationId);
        return new Message(new byte[0], props);
    }

    @Test
    void setsTheCorrelationIdInMdcWhileTheListenerRuns() throws Throwable {
        String correlationId = "abc-123";
        String[] seenInside = new String[1];

        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getArguments()).thenReturn(new Object[]{messageWithHeader(correlationId)});
        when(invocation.proceed()).thenAnswer(inv -> {
            seenInside[0] = MDC.get(KEY);
            return null;
        });

        advice.invoke(invocation);

        assertThat(seenInside[0])
                .as("the listener should see the correlation id in the MDC")
                .isEqualTo(correlationId);
    }

    @Test
    void clearsTheMdcAfterTheListenerReturns() throws Throwable {
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getArguments()).thenReturn(new Object[]{messageWithHeader("abc-123")});
        when(invocation.proceed()).thenReturn(null);

        advice.invoke(invocation);

        assertThat(MDC.get(KEY))
                .as("the correlation id must not linger after processing")
                .isNull();
    }

    @Test
    void clearsTheMdcEvenWhenTheListenerThrows() {
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getArguments()).thenReturn(new Object[]{messageWithHeader("abc-123")});
        try {
            when(invocation.proceed()).thenThrow(new RuntimeException("listener blew up"));
            advice.invoke(invocation);
        } catch (Throwable expected) {
            // propagation is fine; the point is the MDC is cleaned up
        }

        assertThat(MDC.get(KEY)).isNull();
    }

    @Test
    void fallsBackToTheNativeCorrelationIdProperty() throws Throwable {
        String correlationId = "native-9";
        String[] seenInside = new String[1];

        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getArguments()).thenReturn(new Object[]{messageWithNativeCorrelationId(correlationId)});
        when(invocation.proceed()).thenAnswer(inv -> {
            seenInside[0] = MDC.get(KEY);
            return null;
        });

        advice.invoke(invocation);

        assertThat(seenInside[0]).isEqualTo(correlationId);
    }

    @Test
    void leavesMdcUntouchedWhenNoCorrelationIdIsPresent() throws Throwable {
        String[] seenInside = new String[1];

        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getArguments()).thenReturn(new Object[]{messageWithHeader(null)});
        when(invocation.proceed()).thenAnswer(inv -> {
            seenInside[0] = MDC.get(KEY);
            return null;
        });

        advice.invoke(invocation);

        assertThat(seenInside[0]).isNull();
        assertThat(MDC.get(KEY)).isNull();
    }
}
