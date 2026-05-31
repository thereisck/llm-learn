package _7_supervisor_orchestration;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.supervisor.AgentInvocation;
import dev.langchain4j.service.V;

import java.util.List;

public interface HiringSupervisor {
    @Agent("顶层招聘 Supervisor，编排候选人评估和决策流程")
    ResultWithAgenticScope<String> invoke(@V("request") String request, @V("supervisorContext") String supervisorContext);
}