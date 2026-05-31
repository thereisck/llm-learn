package _5_conditional_workflow;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class OrganizingTools {

    @Tool
    public Date getCurrentDate(){
        return new Date();
    }

    @Tool("根据岗位描述ID，查找需要参加现场面试的人员姓名和邮箱地址")
    public List<String> getInvolvedEmployeesForInterview(@P("岗位描述ID") String jobDescriptionId){
        // 模拟实现，仅用于演示
        return new ArrayList<>(List.of(
                "Anna Bolena: hiring.manager@company.com",
                "Chris Durue: near.colleague@company.com",
                "Esther Finnigan: vp@company.com"));
    }

    @Tool("根据员工邮箱地址创建日程条目")
    public void createCalendarEntry(@P("员工邮箱地址列表") List<String> emailAddress, @P("会议主题") String topic, @P("开始时间（格式 yyyy-mm-dd hh:mm）") String start, @P("结束时间（格式 yyyy-mm-dd hh:mm）") String end){
        // 模拟实现，仅用于演示
        System.out.println("*** 日程条目已创建 ***");
        System.out.println("主题: " + topic);
        System.out.println("开始: " + start);
        System.out.println("结束: " + end);
    }

    @Tool
    public int sendEmail(@P("收件人邮箱地址列表") List<String> to, @P("抄送邮箱地址列表") List<String> cc, @P("邮件主题") String subject, @P("邮件正文") String body){
        // 模拟实现，仅用于演示
        System.out.println("*** 邮件已发送 ***");
        System.out.println("收件人: " + to);
        System.out.println("抄送: " + cc);
        System.out.println("主题: " + subject);
        System.out.println("正文: " + body);
        return 1234; // 模拟邮件ID
    }

    @Tool
    public void updateApplicationStatus(@P("岗位描述ID") String jobDescriptionId, @P("候选人姓名（姓, 名）") String candidateName, @P("新的申请状态") String newStatus){
        // 模拟实现，仅用于演示
        System.out.println("*** 申请状态已更新 ***");
        System.out.println("岗位描述ID: " + jobDescriptionId);
        System.out.println("候选人姓名: " + candidateName);
        System.out.println("新状态: " + newStatus);
    }
}