package com.cbq.yatori.core.action.yinghua;


import com.cbq.yatori.core.action.canghui.entity.exam.ExamCourse;
import com.cbq.yatori.core.action.canghui.entity.exam.ExamItem;
import com.cbq.yatori.core.action.canghui.entity.exam.ExamJson;
import com.cbq.yatori.core.action.canghui.entity.exam.ExamTopic;
import com.cbq.yatori.core.action.canghui.entity.examsubmit.TopicAnswer;
import com.cbq.yatori.core.action.canghui.entity.examsubmit.TopicRequest;
import com.cbq.yatori.core.action.canghui.entity.examsubmitrespose.ExamSubmitResponse;
import com.cbq.yatori.core.action.canghui.entity.startexam.StartExam;
import com.cbq.yatori.core.action.yinghua.entity.allcourse.CourseInform;
import com.cbq.yatori.core.action.yinghua.entity.allvideo.NodeList;
import com.cbq.yatori.core.action.yinghua.entity.allvideo.VideoList;
import com.cbq.yatori.core.action.yinghua.entity.allvideo.VideoRequest;
import com.cbq.yatori.core.action.yinghua.entity.examinform.ExamInformRequest;
import com.cbq.yatori.core.action.yinghua.entity.examstart.StartExamRequest;
import com.cbq.yatori.core.action.yinghua.entity.examtopic.ExamTopics;
import com.cbq.yatori.core.action.yinghua.entity.submitstudy.ConverterSubmitStudyTime;
import com.cbq.yatori.core.action.yinghua.entity.submitstudy.SubmitResult;
import com.cbq.yatori.core.action.yinghua.entity.submitstudy.SubmitStudyTimeRequest;
import com.cbq.yatori.core.action.yinghua.entity.videomessage.VideoInformStudyTotal;
import com.cbq.yatori.core.action.yinghua.entity.videomessage.VideoInformRequest;
import com.cbq.yatori.core.entity.*;
import com.cbq.yatori.core.utils.ConfigUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import com.cbq.yatori.core.utils.EmailUtil;

import javax.mail.MessagingException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * @author 长白崎
 * @version 1.0
 * @description: TODO
 * @date 2023/11/2 14:34
 */
@Slf4j
public class CourseStudyAction implements Runnable {


    private User user;
    private Setting setting;
    private CourseInform courseInform; //当前课程的对象

    private VideoRequest courseVideosList; //视屏列表
    //需要看的视屏集合
    private List<NodeList> videoInforms = new ArrayList<>();
    //学习Id
    private long studyId = 0;
    private Boolean newThread = false;


//    private static final Boolean IsOpenmail =ConfigUtils.loadingConfig().getSetting().getEmailInform().getEmail()!="";
    private long studyInterval = 5;

    private Long accoVideo = 0L;
    public void toStudy() {
        CoursesCostom coursesCostom = user.getCoursesCostom();

        //视屏刷课模式
        switch (coursesCostom.getVideoModel()) {
            case 0 -> {
                accoVideo = (long) videoInforms.size();
            }
            //普通模式
            case 1 -> {
                if (newThread) {
                    new Thread(this).start();
                } else {
                    log.info("{}:正在学习课程>>>{}", user.getAccount(), courseInform.getName());
                    study1();
                    log.info("{}:{}学习完毕！", user.getAccount(), courseInform.getName());
                }
            }
            //暴力模式
            case 2 -> {
//                log.info("{}:正在学习课程>>>{}", user.getAccount(), myCourse.getCourse().getTitle());
//                study2();
            }
        }

    }

    @Override
    public void run() {
        log.info("{}:正在学习课程>>>{}", user.getAccount(), courseInform.getName());
        study1();
        if(setting.getEmailInform().getSw()==1){
        try {
            EmailUtil.sendEmail(user.getAccount(),courseInform.getName());
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }}

        log.info("{}:{}学习完毕！", user.getAccount(), courseInform.getName());
    }

    private void study1() {
        AccountCacheYingHua cache = (AccountCacheYingHua) user.getCache();
        for (int i = 0; i < videoInforms.size(); i++) {
            NodeList videoInform = videoInforms.get(i);
            //当视屏没有被锁时
            if (videoInform.getNodeLock() == 0) {
                //如果此视屏看完了则直接跳过
                if (videoInform.getVideoState() == 2) {
                    addAcco();
                    continue;
                }
                //获取到视屏观看信息
                VideoInformRequest videMessage = null;
                while ((videMessage = CourseAction.getVideMessage(user, videoInform)) == null) ;

                //如果videoId为空那么就是考试的，直接自动考试即可
                if(videMessage.getResult().getData().getVideoId().equals("") && user.getCoursesCostom().getAutoExam()==1){
                    ExamInformRequest exam = com.cbq.yatori.core.action.yinghua.ExamAction.getExam(user, String.valueOf(videoInform.getId()));
                    //过滤误判的考试或者视屏
                    if(exam.getExamInformResult().getList().size()!=0) {
                        autoExamAction(courseInform, videoInform, String.valueOf(videoInform.getId()));
                        continue;
                    }
                }
                //视屏总时长
                long videoDuration = videMessage.getResult().getData().getVideoDuration();
                //当前学习进度
                VideoInformStudyTotal studyTotal = videMessage.getResult().getData().getStudyTotal();
                //如果学习总时长超过了视屏总时长那么就跳过
                log.info("正在学习视屏：{}", videoInform.getName());
                //开始看视屏---------------
                long studyTime = Long.parseLong(studyTotal.getDuration());

                //直接先提交一次，这里是为了防止傻逼英华TM自己传的参数都搞错整成了视屏时长为0的问题（傻逼英华，连个参数都传错了）
                CourseAction.submitStudyTime(user, videoInform, studyTime+studyInterval, studyId);
                //循环进行学习
                while ((studyTime += studyInterval) < videoDuration + studyInterval) {
                    //这里根据账号账号登录状态进行策划行为
                    switch (cache.getStatus()) {//未登录则跳出
                        case 0 -> {
                            log.info("账号未登录，禁止刷课！");
                            return;
                        }
                        case 2 -> {//如果登录超时，则堵塞等待
                            studyTime -= studyInterval;
                            continue;
                        }
                    }

                    SubmitStudyTimeRequest submitStudyTimeRequest = CourseAction.submitStudyTime(user, videoInform, studyTime, studyId);
                    try {
                        //如果未成功提交
                        if (submitStudyTimeRequest != null) {
                            //检测是否登录超时
                            if (submitStudyTimeRequest.getMsg().contains("登录超时")) {
                                cache.setStatus(2);
                                studyTime -= studyInterval;
                                continue;
                            }
                            //成功提交
                            SubmitResult result = submitStudyTimeRequest.getResult();
                            //根据反馈内容修改studyId
                            if(result!=null)
                                if(result.getData()!=null)
                                    studyId = result.getData() != null ? result.getData().getStudyId() : studyId;


                            log.info("\n服务器端信息：>>>{}\n学习账号>>>{}\n学习平台>>>{}\n视屏名称>>>{}\n视屏总长度>>>{}\n当前学时>>>{}",
                                    ConverterSubmitStudyTime.toJsonString(submitStudyTimeRequest),
                                    user.getAccount(),
                                    user.getAccountType().name(),
                                    videoInform.getName(),
                                    videoDuration,
                                    studyTime);
                        }

                        //延时8秒
                        if (studyTime < videoDuration) {
                            Thread.sleep(1000 * studyInterval);
                        }
                    } catch (JsonProcessingException e) {
                        log.error("");
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        log.error("");
                        e.printStackTrace();
                    }
                    //更新视屏信息列表
                    if (studyTime >= videoDuration) {
                        if(submitStudyTimeRequest==null)
                            studyTime-=studyInterval;
                        else
                            update();
                    }
                }
            }
            addAcco();
        }
    }

    public void addAcco() {
        synchronized (accoVideo) {
            ++accoVideo;
        }
    }

    public long getAcco() {
        synchronized (accoVideo) {
            return this.accoVideo;
        }
    }




    private void update() {
        //初始化视屏列表
        while ((courseVideosList = CourseAction.getCourseVideosList(user, courseInform)) == null) ;
        //章节
        List<VideoList> zList = courseVideosList.getResult().getList();
        //将所有视屏都加入到集合里面
        videoInforms.clear();
        for (VideoList videoList : zList) {
            for (NodeList videoInform : videoList.getNodeList()) {
                videoInforms.add(videoInform);
            }
        }
    }

    /**
     * 自动考试
     */
    public void autoExamAction(CourseInform courseInform, NodeList videoInform,String nodeId) {
        log.info("{}:正在考试课程>>>{}", user.getAccount(), courseInform.getName());
        ExamInformRequest exam = com.cbq.yatori.core.action.yinghua.ExamAction.getExam(user, nodeId);
        String examId= String.valueOf(exam.getExamInformResult().getList().get(0).getId());
        String courseId =String.valueOf(exam.getExamInformResult().getList().get(0).getCourseId());
        StartExamRequest startExamRequest = com.cbq.yatori.core.action.yinghua.ExamAction.startExam(user, courseId, nodeId, examId);//开始考试
        ExamTopics examTopics = com.cbq.yatori.core.action.yinghua.ExamAction.getExamTopic(user, nodeId, examId);//获取题目

        List<String> list = examTopics.getExamTopics().keySet().stream().toList();
        for(int i= 0;i<list.size();++i){
            String answer = com.cbq.yatori.core.action.yinghua.ExamAction.aiAnswerFormChatGLM(setting.getAiSetting().getAPI_KEY(), examTopics.getExamTopics().get(list.get(i)));
            answer=answer.replace("\n","");
            answer = answer.replace(" ","");
            ExamAction.submitExam(user, examId, examTopics.getExamTopics().get(list.get(i)).getAnswerId(), answer, (i+1)<list.size()?"0":"1");
        }
        log.info("{}:课程:{}考试成功！对应考试试卷{}，服务器信息：{}", user.getAccount(), courseInform.getName(), videoInform.getName());
    }

    public static Builder builder() {
        return new Builder();
    }


    public static class Builder {
        private CourseStudyAction courseStudyAction = new CourseStudyAction();

        public Builder user(User user) {
            courseStudyAction.user = user;
            return this;
        }

        public Builder courseInform(CourseInform courseInform) {
            courseStudyAction.courseInform = courseInform;
            return this;
        }

        public Builder newThread(Boolean newThread) {
            courseStudyAction.newThread = newThread;
            return this;
        }

        public Builder setting(Setting setting){
            courseStudyAction.setting = setting;
            return this;
        }
        public CourseStudyAction build() {
            //初始化视屏列表
            courseStudyAction.courseVideosList = null;
            while ((courseStudyAction.courseVideosList = CourseAction.getCourseVideosList(courseStudyAction.user, courseStudyAction.courseInform)) == null) ;

            //章节
            List<VideoList> zList = courseStudyAction.courseVideosList.getResult().getList();
            //将所有视屏都加入到集合里面
            for (VideoList videoList : zList) {
                for (NodeList videoInform : videoList.getNodeList()) {
                    courseStudyAction.videoInforms.add(videoInform);
                }
            }

            return courseStudyAction;
        }
    }
}
