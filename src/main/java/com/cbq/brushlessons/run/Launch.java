package com.cbq.brushlessons.run;

import com.cbq.brushlessons.core.action.yinghua.CourseAction;
import com.cbq.brushlessons.core.action.yinghua.CourseStudyAction;
import com.cbq.brushlessons.core.action.yinghua.LoginAction;
import com.cbq.brushlessons.core.action.yinghua.entity.allcourse.CourseInform;
import com.cbq.brushlessons.core.action.yinghua.entity.allcourse.CourseRequest;
import com.cbq.brushlessons.core.entity.AccountCacheYingHua;
import com.cbq.brushlessons.core.entity.Config;
import com.cbq.brushlessons.core.entity.User;
import com.cbq.brushlessons.core.utils.ConfigUtils;
import com.cbq.brushlessons.core.utils.FileUtils;
import com.cbq.brushlessons.core.utils.VerificationCodeUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author 长白崎
 * @version 1.0
 * @description: 加载启动程序
 * @date 2023/10/31 8:45
 */
@Slf4j
public class Launch {
    private Config config;

    static {
        System.out.println("""
                                             ___                               \s
                        ,---,              ,--.'|_                      ,--,   \s
                       /_ ./|              |  | :,'   ,---.    __  ,-.,--.'|   \s
                 ,---, |  ' :              :  : ' :  '   ,'\\ ,' ,'/ /||  |,    \s
                /___/ \\.  : |   ,--.--.  .;__,'  /  /   /   |'  | |' |`--'_    \s
                 .  \\  \\ ,' '  /       \\ |  |   |  .   ; ,. :|  |   ,',' ,'|   \s
                  \\  ;  `  ,' .--.  .-. |:__,'| :  '   | |: :'  :  /  '  | |   \s
                   \\  \\    '   \\__\\/: . .  '  : |__'   | .; :|  | '   |  | :   \s
                    '  \\   |   ," .--.; |  |  | '.'|   :    |;  : |   '  : |__ \s
                     \\  ;  ;  /  /  ,.  |  ;  :    ;\\   \\  / |  , ;   |  | '.'|\s
                      :  \\  \\;  :   .'   \\ |  ,   /  `----'   ---'    ;  :    ;\s
                       \\  ' ;|  ,     .-./  ---`-'                    |  ,   / \s
                        `--`  `--`---'                                 ---`-'  \s
                                Yatori v1.0.2-Beta-1
                """);
    }

    /**
     * 初始化数据
     */
    public void init() {
        //加载配置文件
        config = ConfigUtils.loadingConfig();
    }

    public void toRun() {
        //获取账号列表-----------------------------
        List<User> users = config.getUsers();


        //先进行登录----------------------------------------------
        for (User user : users) {
            switch (user.getAccountType()) {
                case YINGHUA -> {
                    AccountCacheYingHua accountCacheYingHua = new AccountCacheYingHua();
                    user.setCache(accountCacheYingHua);
                    //refresh_code:1代表密码错误，
                    Map<String, Object> result = null;
                    do {
                        //获取SESSION
                        String session = null;
                        while ((session = LoginAction.getSESSION(user)) == null) ;
                        accountCacheYingHua.setSession(session);
                        //获取验证码
                        File code = null;
                        while ((code = LoginAction.getCode(user)) == null) ;
                        accountCacheYingHua.setCode(VerificationCodeUtil.aiDiscern(code));
                        FileUtils.deleteFile(code);//删除验证码文件
                        //进行登录操作
                        while ((result = LoginAction.toLogin(user)) == null) ;
                    } while (!(Boolean) result.get("status") && ((String) result.get("msg")).contains("验证码有误"));
                    //对结果进行判定
                    if ((Boolean) result.get("status")) {
                        accountCacheYingHua.setStatus(1);
                        log.info("{}登录成功！", user.getAccount());
                    } else {
                        log.info("{}登录失败，服务器信息>>>{}", user.getAccount(), ((String) result.get("msg")));
                        return;
                    }

                    //为账号维持登录状态-----------------------------------
                    new Thread(() -> {
                        while (true) {
                            Map online;
                            //避免超时
                            while ((online = LoginAction.online(user)) == null) {
                                try {
                                    Thread.sleep(1000 * 5);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                            //如果含有登录超时字样
                            if (((String) online.get("msg")).contains("更新成功")) {
                                accountCacheYingHua.setStatus(1);
                            } else if (((String) online.get("msg")).contains("登录超时")) {
                                accountCacheYingHua.setStatus(2);//设定登录状态为超时
                                log.info("{}登录超时，正在重新登录...", user.getAccount());
                                //进行登录
                                Map<String, Object> map;
                                do {
                                    //获取验证码
                                    File code = LoginAction.getCode(user);
                                    ((AccountCacheYingHua) user.getCache()).setCode(VerificationCodeUtil.aiDiscern(code));
                                    FileUtils.deleteFile(code);//删除验证码文件
                                    //进行登录操作
                                    map = LoginAction.toLogin(user);
                                } while (!(Boolean) map.get("status") && ((String) map.get("msg")).contains("验证码有误"));
                                //对结果进行判定
                                if ((Boolean) map.get("status")) {
                                    accountCacheYingHua.setStatus(1);
                                    log.info("{}登录成功！", user.getAccount());
                                } else {
                                    log.info("{}登录失败，服务器信息>>>{}", user.getAccount(), ((String) map.get("msg")));
                                }
                            }
                            try {
                                Thread.sleep(1000 * 60);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }).start();
                }
            }
        }

        //刷课
        for (User user : users) {
            switch (user.getAccountType()) {
                case YINGHUA -> {
                    new Thread(()->{
                        //获取全部课程
                        CourseRequest allCourseList = null;
                        while((allCourseList=CourseAction.getAllCourseRequest(user))==null);

                        for (CourseInform courseInform : allCourseList.getResult().getList()) {
                            //课程排除配置
                            if (user.getExcludeCourses() != null) {
                                if (user.getExcludeCourses().size() != 0)
                                    if (user.getExcludeCourses().contains(courseInform.getName()))
                                        continue;
                            }
                            //如果有指定课程包含设定，那么就执行
                            if (user.getIncludeCourses() != null) {
                                if (user.getIncludeCourses().size() != 0)
                                    if (!user.getIncludeCourses().contains(courseInform.getName()))
                                        continue;
                            }
                            CourseStudyAction bulild = CourseStudyAction.builder()
                                    .user(user)
                                    .courseInform(courseInform)
                                    .newThread(true)
                                    .build();
                            bulild.toStudy();
                        }
                    }).start();
                }
            }

        }

        //不让程序结束运行
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
            }
        }, 60 * 1000, 1000);
    }


}
