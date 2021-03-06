package org.pqh.test;

import org.apache.commons.io.FileUtils;
import org.pqh.entity.Email;
import org.pqh.entity.Save;
import org.pqh.service.InsertService;
import org.pqh.task.Listener;
import org.pqh.task.TaskBili;
import org.pqh.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.Scanner;

import static org.pqh.util.SpringContextHolder.biliDao;

/**
 * Created by Reborn on 2016/2/5.
 */

@Component
public class Test {


    @Value("${7zpwd}")
    private String _7zpwd;
    @Value("${localPath}")
    private String localPath;
    @Value("${dbusername}")
    private String dbusername;
    @Value("${dbpassword}")
    private String dbpassword;
    @Value("${backuptables}")
    private String backuptables;
    @Value("${serverPath}")
    private String serverPath;
    @Value("${exclude}")
    private String exclude;
    @Value("${mysqlPath}")
    private String mysqlPath;

    public static void main(String[] args) throws Exception {
        LogUtil.getLogger().info("开始爬虫程序");
        new Test().testTask();
        SpringContextHolder.close();
        System.exit(0);
        LogUtil.getLogger().info("结束爬虫程序");

    }


    public  void testTask(){
        //爬取历史接口数据
        Listener listener=new Listener();
        TaskBili taskBili=new TaskBili(4);
        taskBili.addObserver(listener);
        Thread thread=new Thread(taskBili,"insertHistory");
        ThreadUtil.excute(thread);

//        Thread insertCid=new Thread(()->{
//            ThreadUtil.addTask(2);
//        },"insertCid");
//        ThreadUtil.excute(insertCid);



        ThreadUtil.excute(()->{
            while (true){
                Scanner scanner=new Scanner(System.in);

                String command=scanner.nextLine();
                LogUtil.getLogger().info("输入命令："+command);
                switch (command){
                    case "reloadLog4jConfig":LogUtil.reloadLog4jConfig();
                }


            }

        });

        //定时检测爬虫运行状态，不正常自动重启线程读取记录表记录继续爬取。
        String s=null;
        while(true) {
            int time=PropertiesUtil.getProperties("checktime",Integer.class);

            ThreadUtil.sleep("检测爬虫程序状态",time);

            for (Save save : biliDao.selectSave(null)) {
                if(save.getId()==4){
                    if(s==null){
                        s=save.getBilibili();
                    }else if(!s.equals(save.getBilibili())){
                        s=save.getBilibili();
                    }else{
                        Email email=new Email("爬虫系统信息",new StringBuffer("爬虫程序出现异常</br>"+String.valueOf(save)));
                        EmailUtil.sendEmail(email);
                        InsertService.stop=true;
                    }

                }
                LogUtil.getLogger().info(String.valueOf(save));
            }
        }
    }

    /**
     * 备份数据库
     */

    public  void backupMysql(){
        Date date=new Date();
        String date_1=TimeUtil.formatDate(date,"HH_mm_ss");
        String date_2=TimeUtil.formatDate(date);

        //当前日期年月日作为备份数据库的目录
        String todayDir=localPath+date_2+"/";

        //当前日期时分秒作为备份数据库文件的文件名
        File sqlFile=new File(todayDir+date_1+".sql");

        //调用mysqldump备份命令备份数据库
        //运行备份命令
        try {
            FileUtils.writeStringToFile(sqlFile,null,"GBK");
        } catch (IOException e) {
            e.printStackTrace();
        }
        String command="mysqldump --default-character-set=utf8 -u"+dbusername+" -p"+dbpassword+" bilibili "+backuptables+">"+sqlFile.getAbsolutePath();

        RunCommandUtil.runCommand(command);

        //每天定时打包一次数据库放到服务器
        File _7zFile=new File(serverPath+date_2+"/"+date_1+".7z");
        //打包sql文件
        RunCommandUtil.compress(_7zFile,sqlFile,_7zpwd);

        Email email=new Email("爬虫系统信息",new StringBuffer("数据库备份成功\n"+_7zFile.getPath()));
        EmailUtil.sendEmail(email);
        //上传sql到百度云
//        uploadBdu(serverPath);
        String oldDirs[]=new String[]{localPath,serverPath};
        for(String dir:oldDirs) {
            for (File subdir : new File(dir).listFiles()) {
                if (FileUtils.isFileOlder(subdir, date)) {
                    try {
                        FileUtils.deleteDirectory(subdir);
                    } catch (IOException e) {
                        LogUtil.getLogger().error("无法删除旧备份目录" + e.getMessage());
                    }
                    LogUtil.getLogger().info("删除旧备份目录" + subdir.getAbsoluteFile());
                }
            }
        }

        //临时文件，选中扩展名为out格式的文件
        Collection<File> fileList=FileUtils.listFiles(FileUtils.getTempDirectory(),new String[]{"out"},true);
        for(File f:fileList){
            //确认是idea产生的临时文件则删除
            if(f.getName().contains("idea")){
                try {
                    FileUtils.forceDelete(f);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        BiliUtil.updateAccesskey();
    }



}
