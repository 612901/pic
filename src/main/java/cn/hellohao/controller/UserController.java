package cn.hellohao.controller;

import cn.hellohao.auth.filter.SubjectFilter;
import cn.hellohao.auth.token.JWTUtil;
import cn.hellohao.config.SysName;
import cn.hellohao.pojo.*;
import cn.hellohao.service.*;
import cn.hellohao.utils.*;
import cn.hutool.captcha.ShearCaptcha;
import cn.hutool.core.util.HexUtil;
import com.alibaba.fastjson.JSONObject;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;


@Controller
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;
    @Autowired
    private EmailConfigService emailConfigService;
    @Autowired
    private ConfigService configService;
    @Autowired
    private UploadConfigService uploadConfigService;
    @Autowired
    private SysConfigService sysConfigService;
    @Autowired
    IRedisService iRedisService;

    @PostMapping("/register")
    @ResponseBody
    public Msg Register(HttpServletRequest httpServletRequest, @RequestParam(value = "data", defaultValue = "") String data) {
        Msg msg = new Msg();
        JSONObject jsonObj = JSONObject.parseObject(data);
        String username = jsonObj.getString("username");
        String email = jsonObj.getString("email");
        String password = Base64Encryption.encryptBASE64(jsonObj.getString("password").getBytes());
        String verifyCodeForRegister = jsonObj.getString("verifyCode");
        Object redis_verifyCodeForRegister = iRedisService.getValue("verifyCodeForRegister_"+httpServletRequest.getHeader("verifyCodeForRegister"));
        if(!SetText.checkEmail(email)){
            //?????????????????????
            msg.setCode("110403");
            msg.setInfo("?????????????????????");
            return msg;
        }
        String regex = "^\\w+$";
        if(username.length()>20 || !username.matches (regex)){
            //????????????????????????
            msg.setCode("110403");
            msg.setInfo("?????????????????????20?????????");
            return msg;
        }
        if(null==redis_verifyCodeForRegister){
            msg.setCode("4035");
            msg.setInfo("??????????????????????????????????????????");
            return msg;
        }else if(null==verifyCodeForRegister){
            msg.setCode("4036");
            msg.setInfo("????????????????????????");
            return msg;
        }
        if((redis_verifyCodeForRegister.toString().toLowerCase()).compareTo((verifyCodeForRegister.toLowerCase()))==0){
            User user = new User();
            UploadConfig updateConfig = uploadConfigService.getUpdateConfig();
            EmailConfig emailConfig = emailConfigService.getemail();
            Integer countusername = userService.countusername(username);
            Integer countmail = userService.countmail(email);
            SysConfig sysConfig = sysConfigService.getstate();
            if(sysConfig.getRegister()!=1){
                msg.setCode("110403");
                msg.setInfo("???????????????????????????????????????");
                return msg;
            }
            if(countusername == 1 || !SysName.CheckSysName(username)){
                msg.setCode("110406");
                msg.setInfo("?????????????????????");
                return msg;
            }
            if(countmail == 1){
                msg.setCode("110407");
                msg.setInfo("?????????????????????");
                return msg;
            }
            String uid = UUID.randomUUID().toString().replace("-", "").toLowerCase();
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//??????????????????
            String birthder = df.format(new Date());// new Date()???????????????????????????
            user.setLevel(1);
            user.setUid(uid);
            user.setBirthder(birthder);
            user.setMemory(updateConfig.getUsermemory());
            user.setGroupid(1);
            user.setEmail(email);
            user.setUsername(username);
            user.setPassword(password);
            user.setToken(UUID.randomUUID().toString().replace("-", ""));
            Config config = configService.getSourceype();
            Integer type = 0;
            if(emailConfig.getUsing()==1){
                user.setIsok(0);
                //????????????????????????
                Thread thread = new Thread() {
                    public void run() {
                        Integer a = NewSendEmail.sendEmail(emailConfig,user.getUsername(), uid, user.getEmail(),config);
                    }
                };
                thread.start();
                msg.setInfo("????????????,???????????????????????????????????????");
            }else{
                //????????????
                user.setIsok(1);
                msg.setInfo("????????????,???????????????");
            }
            userService.register(user);
        }else{
            msg.setCode("110408");
            msg.setInfo("??????????????????");//?????????????????????
        }
        return msg;
    }

    @PostMapping("/login")//new
    @ResponseBody
    public Msg login(HttpServletRequest httpServletRequest,@RequestParam(value = "data", defaultValue = "") String data) {
        Msg msg = new Msg();
        JSONObject jsonObj = JSONObject.parseObject(data);
        String email = jsonObj.getString("email");
        String password = Base64Encryption.encryptBASE64(jsonObj.getString("password").getBytes());
        String verifyCode = jsonObj.getString("verifyCode");
        if(null == email || null == password || null == verifyCode){
            msg.setCode("5000");
            msg.setInfo("?????????????????????");
            return msg;
        }
        if(email.replace(" ","").equals("") || password.replace(" ","").equals("")
                || verifyCode.replace(" ","").equals("")){
            msg.setCode("5000");
            msg.setInfo("?????????????????????");
            return msg;
        }

        Object redis_VerifyCode = iRedisService.getValue("verifyCode_"+httpServletRequest.getHeader("verifyCode"));
        if(null==redis_VerifyCode){
            msg.setCode("4035");
            msg.setInfo("??????????????????????????????????????????");
            return msg;
        }else if(null==verifyCode){
            msg.setCode("4036");
            msg.setInfo("????????????????????????");
            return msg;
        }
        if((redis_VerifyCode.toString().toLowerCase()).compareTo((verifyCode.toLowerCase()))==0){
            Subject subject = SecurityUtils.getSubject();
            UsernamePasswordToken tokenOBJ = new UsernamePasswordToken(email,password);
            tokenOBJ.setRememberMe(true);
            try {
                subject.login(tokenOBJ);
                SecurityUtils.getSubject().getSession().setTimeout(3600000);
                JSONObject jsonObject = new JSONObject();
                User user = (User) SecurityUtils.getSubject().getPrincipal();
                if(user.getIsok()==0){
                    msg.setInfo("????????????????????????");
                    msg.setCode("110403");
                    return msg;
                }
                if(user.getIsok()<0){
                    msg.setInfo("????????????????????????");
                    msg.setCode("110403");
                    return msg;
                }
                String token = JWTUtil.createToken(user);
                Subject su = SecurityUtils.getSubject();
                System.out.println("?????????????????????admin:"+su.hasRole("admin"));
                msg.setInfo("????????????");
                jsonObject.put("token",token);
                jsonObject.put("RoleLevel",user.getLevel()==2?"admin":"user");
                jsonObject.put("userName",user.getUsername());
                msg.setData(jsonObject);
                return msg;
            } catch (UnknownAccountException e) {
                //?????????????????????????????????
                msg.setCode("4000");
                msg.setInfo("?????????????????????");
                System.err.println("???????????????");
                e.printStackTrace();
                return msg;
            }catch (IncorrectCredentialsException e) {
                msg.setCode("4000");
                msg.setInfo("??????????????????");
                System.err.println("???????????????");
                e.printStackTrace();
                return msg;
            }catch (Exception e) {
                msg.setCode("5000");
                msg.setInfo("????????????");
                System.err.println("????????????");
                e.printStackTrace();
                return msg;
            }
        }else{
            msg.setCode("40034");
            msg.setInfo("??????????????????");
        }
        return msg;
    }


    @RequestMapping(value = "/activation", method = RequestMethod.GET)
    public String activation(Model model, HttpServletRequest request, HttpSession session, String activation, String username) {
        Config config = configService.getSourceype();
        Integer ret = 0;
        User u2 = new User();
        u2.setUid(activation);
        User user = userService.getUsers(u2);
        model.addAttribute("webhost",SubjectFilter.WEBHOST);
        if (user != null && user.getIsok() == 0) {
            userService.uiduser(activation);
            model.addAttribute("title","????????????");
            model.addAttribute("name","Hi~"+username);
            model.addAttribute("note","??????????????????????????????");
            return "msg";
        } else {
            model.addAttribute("title","????????????");
            model.addAttribute("name","????????????????????????");
            model.addAttribute("note","???????????????");
            return "msg";
        }
    }

    @PostMapping(value = "/logout")//new
    @ResponseBody
    public Msg exit(Model model, HttpServletRequest request, HttpServletResponse response, HttpSession session) {
        Msg msg = new Msg();
        Subject subject = SecurityUtils.getSubject();
        subject.logout();
        msg.setInfo("????????????");
        Print.Normal("????????????????????????");
        return msg;
    }

    @PostMapping("/retrievePass")
    @ResponseBody
    public Msg retrievePass(HttpServletRequest httpServletRequest, @RequestParam(value = "data", defaultValue = "") String data) {
        Msg msg = new Msg();
        try {
            JSONObject jsonObj = JSONObject.parseObject(data);
            String email = jsonObj.getString("email");
            String retrieveCode = jsonObj.getString("retrieveCode");
            Object redis_verifyCodeForEmailRetrieve =iRedisService.getValue("verifyCodeForRetrieve_"+httpServletRequest.getHeader("verifyCodeForRetrieve"));
            EmailConfig emailConfig = emailConfigService.getemail();
            if(null==redis_verifyCodeForEmailRetrieve){
                msg.setCode("4035");
                msg.setInfo("??????????????????????????????????????????");
                return msg;
            }else if(null==retrieveCode){
                msg.setCode("4036");
                msg.setInfo("????????????????????????");
                return msg;
            }
            if((redis_verifyCodeForEmailRetrieve.toString().toLowerCase()).compareTo((retrieveCode.toLowerCase()))!=0){
                msg.setCode("40034");
                msg.setInfo("??????????????????");
                return msg;
            }
            Integer ret = userService.countmail(email);
            if(ret>0){
                if(emailConfig.getUsing()==1){
                    User u2 = new User();
                    u2.setEmail(email);
                    User user = userService.getUsers(u2);
                    if(user.getIsok()==-1){
                        msg.setCode("110110");
                        msg.setInfo("???????????????????????????????????????");
                        return msg;
                    }
                    Config config = configService.getSourceype();
                    Thread thread = new Thread() {
                        public void run() {
                            Integer a = NewSendEmail.sendEmailFindPass(emailConfig,user.getUsername(), user.getUid(), user.getEmail(),config);
                        }
                    };
                    thread.start();
                    msg.setInfo("????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                }else{
                    msg.setCode("400");
                    msg.setInfo("???????????????????????????????????????????????????");
                }
            }else{
                msg.setCode("110404");
                msg.setInfo("??????????????????????????????");
            }
        }catch (Exception e){
            e.printStackTrace();
            msg.setCode("110500");
            msg.setInfo("??????????????????");
        }
        return msg;
    }

    @RequestMapping(value = "/retrieve", method = RequestMethod.GET)
    public String retrieve(Model model, String activation,String cip) {
        Integer ret = 0;
        try {
            User u2 = new User();
            u2.setUid(activation);
            User user = userService.getUsers(u2);
            user.setIsok(1);
            String new_pass = HexUtil.decodeHexStr(cip);//????????????
            user.setPassword(Base64Encryption.encryptBASE64(new_pass.getBytes()));
            String uid = UUID.randomUUID().toString().replace("-", "").toLowerCase();
            user.setUid(uid);
            if (user != null) {
                Integer r = userService.changeUser(user);
                model.addAttribute("title","??????");
                model.addAttribute("name","?????????:"+new_pass);//
                model.addAttribute("note","???????????????????????????????????????????????????????????????");
            } else {
                model.addAttribute("title","??????");
                model.addAttribute("name","????????????????????????");
                model.addAttribute("note","????????????");
            }
        }catch (Exception e){
            e.printStackTrace();
            model.addAttribute("title","??????");
            model.addAttribute("name","?????????????????????????????????");
            model.addAttribute("note","????????????");
        }
        model.addAttribute("webhost", SubjectFilter.WEBHOST);
        return "msg";
    }



}
