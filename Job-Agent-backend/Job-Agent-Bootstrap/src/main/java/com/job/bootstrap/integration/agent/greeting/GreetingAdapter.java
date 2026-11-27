package com.job.bootstrap.integration.agent.greeting;

import com.job.agent.port.GreetingPort;
import com.job.bootstrap.service.JobGreetingService;
import com.job.common.vo.greeting.GreetingVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 功能: Agent 话术生成端口的宿主实现，委托现有 JobGreetingService。 */
@Component
@RequiredArgsConstructor
public class GreetingAdapter implements GreetingPort {

    private final JobGreetingService jobGreetingService;

    @Override
    public GreetingVO generateGreeting(Long userId, Long resumeId, Long jobId, String style) {
        return jobGreetingService.generateGreeting(userId, resumeId, jobId, style);
    }
}
