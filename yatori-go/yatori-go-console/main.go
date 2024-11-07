package main

import (
	"encoding/json"
	"fmt"
	"github.com/thedevsaddam/gojsonq"
	"log"
	"os"
	"strconv"
	"sync"
	time2 "time"
	"yatori-go-console/config"
	"yatori-go-core/aggregation/yinghua"
	yinghuaApi "yatori-go-core/api/yinghua"
	"yatori-go-core/utils"
)

var videosLock sync.WaitGroup
var usersLock sync.WaitGroup

func main() {
	fmt.Println(config.YaotirLogo())
	utils.NOWLOGLEVEL = utils.INFO //设置日志登记为INFO
	content, err := os.ReadFile("./config.json")
	if err != nil {
		log.Fatal(err)
	}

	var configJson config.JSONDataForConfig
	err = json.Unmarshal(content, &configJson)
	if err != nil {
		log.Fatal(err)
	}
	for _, user := range configJson.Users {
		if user.AccountType == "YINGHUA" {
			usersLock.Add(1)
			go userBlock(user)
		}
	}
	usersLock.Wait()
}

func userBlock(user config.Users) {
	//测试账号
	cache := yinghuaApi.UserCache{PreUrl: user.URL, Account: user.Account, Password: user.Password}
	error := yinghua.LoginAction(&cache) // 登录
	if error != nil {
		log.Fatal(error) //登录失败则直接退出
	}
	go keepAliveLogin(cache)                   //携程保活
	list, _ := yinghua.CourseListAction(cache) //拉取课程列表
	for _, item := range list {
		videosLock.Add(1)
		utils.LogPrintln(utils.INFO, " ", item.Id, " ", item.Name, " ", strconv.FormatFloat(item.Progress, 'b', 5, 32), " ", item.StartDate.String(), " ", strconv.Itoa(item.VideoCount), " ", strconv.Itoa(item.VideoLearned))
		go videoListStudy(cache, item) //多携程刷课
	}
	videosLock.Wait()
}

// 用于登录保活
func keepAliveLogin(userCache yinghuaApi.UserCache) {
	for {
		api := yinghuaApi.KeepAliveApi(userCache)
		utils.LogPrintln(utils.INFO, " ", "登录保活状态：", api)
		time2.Sleep(time2.Second * 60)
	}
}

// 刷视频的抽离函数
func videoListStudy(userCache yinghuaApi.UserCache, course yinghua.YingHuaCourse) {
	videoList, _ := yinghua.VideosListAction(userCache, course) //拉取对应课程的视屏列表

	// 提交学时
	for _, video := range videoList {
		utils.LogPrintln(utils.INFO, " ", video.Name)
		time := video.ViewedDuration //设置当前观看时间为最后看视屏的时间
		studyId := "0"
		for {
			if video.Progress == 100 {
				break //如果看完了，也就是进度为100那么直接跳过
			}
			sub := yinghuaApi.SubmitStudyTimeApi(userCache, video.Id, studyId, time) //提交学时
			if gojsonq.New().JSONString(sub).Find("msg") != "提交学时成功!" {
				time2.Sleep(5 * time2.Second)
				continue
			}

			studyId = strconv.Itoa(int(gojsonq.New().JSONString(sub).Find("result.data.studyId").(float64)))
			utils.LogPrintln(utils.INFO, " ", video.Name, " ", "提交状态：", gojsonq.New().JSONString(sub).Find("msg").(string), " ", "观看时间：", strconv.Itoa(time)+"/"+strconv.Itoa(video.VideoDuration), " ", "观看进度：", fmt.Sprintf("%.2f", float32(time)/float32(video.VideoDuration)*100), "%")
			time += 5
			time2.Sleep(5 * time2.Second)
			if time > video.VideoDuration {
				break //如果看完该视屏则直接下一个
			}
		}
	}
	videosLock.Done()
}
