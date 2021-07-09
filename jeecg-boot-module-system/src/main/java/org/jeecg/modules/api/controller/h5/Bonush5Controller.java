package org.jeecg.modules.api.controller.h5;


import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jeecg.dingtalk.api.core.response.Response;
import com.jeecg.dingtalk.api.core.util.ApiUrls;
import com.jeecg.dingtalk.api.core.util.HttpUtil;
import com.jeecg.dingtalk.api.department.JdtDepartmentAPI;
import com.jeecg.dingtalk.api.department.vo.Department;
import com.jeecg.dingtalk.api.user.JdtUserAPI;
import com.jeecg.dingtalk.api.user.vo.User;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.api.dto.message.MessageDTO;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.modules.bonus.entity.BonusDetail;
import org.jeecg.modules.bonus.entity.BonusStaff;
import org.jeecg.modules.bonus.service.IBonusDetailService;
import org.jeecg.modules.bonus.service.IBonusStaffService;
import org.jeecg.modules.system.service.impl.ThirdAppDingtalkServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;


@Api(tags = "激励金微应用测试")
@RestController
@RequestMapping("/bonus/h5Api")
@Slf4j
public class Bonush5Controller {
    private static final String BASE_CHECK_CODES = "qwertyuiplkjhgfdsazxcvbnmQWERTYUPLKJHGFDSAZXCVBNM1234567890";


    @Autowired
    ThirdAppDingtalkServiceImpl dingtalkService;

    @Autowired
    RedisUtil redisUtil;

    @Autowired
    IBonusStaffService bonusStaffService;

    @Autowired
    IBonusDetailService bonusDetailService;


    //2. 服务端通过钉钉消息功能发送验证码，并校验验证码    --
    @AutoLog(value = "钉钉消息功能发送验证码")
    @ApiOperation(value = "钉钉消息功能发送验证码", notes = "(请求参数:jobNum)根据员工工号发送验证码？暂时我为钉钉第三方发送code？--结果成功或失败")
    @PostMapping("/sendDingDingCode")
    Result sendDingDingCode(@RequestBody JSONObject jsonObject) {
        String jobNum = jsonObject.getString("jobNum");
        //工号为空
        if (StringUtils.isEmpty(jobNum)) return Result.error("发送失败,工号为空");

        //根据工号查询查询用户
        BonusStaff bonusStaff = new BonusStaff();
        bonusStaff.setJobnum(jobNum);
        BonusStaff one = bonusStaffService.getOne(new QueryWrapper<BonusStaff>(bonusStaff));
        String toUser = one.getDingdingid();//获取用户id

        //产生一个验证码
        String code = RandomUtil.randomString(BASE_CHECK_CODES, 4);
        MessageDTO messageDTO = new MessageDTO();
        messageDTO.setContent("验证码是" + code + "有效时间为5分钟");
        messageDTO.setToUser(toUser);
//        ------------------------暂时我为钉钉第三方发送code 弃用----------------------------------
        boolean b = dingtalkService.sendMessage(messageDTO, true);//发送消息
//        使用redis保存验证码，并进行校验
        redisUtil.set(jobNum, code, 5 * 60);//往redis存入登录信息，设置过期时间5分钟

        return Result.OK("发送成功",code);
    }

    //校验钉钉验证码
    @AutoLog(value = "钉钉校验验证码")
    @ApiOperation(value = "钉钉校验验证码", notes = "(请求参数:jobNum,checkCode)通过员工工号与验证码进行校验-结果集为true或flase")
    @PostMapping("/verifyDingDingCode")
    Result verifyDingDingCode(@RequestBody JSONObject jsonObject) {
        String jobNum = jsonObject.getString("jobNum");
        String checkCode = jsonObject.getString("checkCode");
        if (StringUtils.isEmpty(jobNum) && StringUtils.isEmpty(checkCode)) return Result.error("工号或验证码不存在");
        String RedisCode = (String) redisUtil.get(jobNum);
        Result<String> result = new Result<>();

        boolean b = StringUtils.equalsIgnoreCase(checkCode, RedisCode);
        if (!b) {//验证失败
            result.setMessage("操作失败");
            result.setSuccess(false);
            result.setResult("验证失败");
            return result;
        }

        BonusStaff bonusStaff = new BonusStaff();
        bonusStaff.setJobnum(jobNum);
        BonusStaff one = bonusStaffService.getOne(new QueryWrapper<BonusStaff>(bonusStaff));
//        String password = one.getPas();

        return Result.OK("校验码正确");
    }

    //模拟微应用免登录请求第三方code --弃用
//    @ApiOperation(value = "模拟微应用免登录--模拟请求钉钉code", notes = "请求钉钉接口改为请求本地后台--结果集为true或flase")
//    @PostMapping("/isLoginCode")
//    Result isLoginCode() {
//        //产生一个验证码
//        String code = RandomUtil.randomString(BASE_CHECK_CODES, 4);
//        boolean isLoginCode = redisUtil.set("isLoginCode", code, 60 * 5);//过期时间设置为5分钟
//
//        return Result.OK("请求模拟钉钉成功", code);
//    }


    //微应用登入密码   --免登录
    @AutoLog(value = "微应用免登录")
    @ApiOperation(value = "微应用免登录", notes = "(请求参数:code)钉钉微应用免登录接口,入参需要第三方code--结果集为bonus_staff对象")
    @PostMapping("/isLogin")
    Result isLogin(@RequestBody JSONObject jsonObject) {
        String code = jsonObject.getString("code");
        //code是免登授权码
        if (StringUtils.isEmpty(code)) return Result.error("没有授权码");

        //获取企业凭证
        String accessToken = dingtalkService.getAccessToken();

        //发送请求，通过钉钉API携带accesstoken与code查询用户信息
        String url = ApiUrls.get("https://oapi.dingtalk.com/topapi/v2/user/getuserinfo?access_token=%s", new Object[]{accessToken});
        JSONObject body = new JSONObject();
        body.put("code", code);

        Response<JSONObject> originResponse = HttpUtil.post(url, body.toJSONString(), new Type[]{User.class});
//        Response<JSONObject> originResponse = HttpUtil.post(url, body.toJSONString(), new Type[]{User.class});
        String userId = ((JSONObject) originResponse.getResult()).getString("userid").toString();//获取用户id
        log.info("UserId是：" + userId);
        Object objCode = redisUtil.get("isLoginCode");
        String isLoginCode = (String) objCode;
        if (!isLoginCode.equals(code)) return Result.error("授权码错误");
//        String userId = "admin";

        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("dingdingID", userId);
        queryWrapper.isNotNull("pas");
        BonusStaff one = bonusStaffService.getOne(queryWrapper);
        if (one == null) {
            //初次登陆，填装信息
            //通过钉钉接口获取个人信息
            Response<User> result1 = JdtUserAPI.getUserById(userId, accessToken);
            User result = result1.getResult();

            //填充数据
            one.setStaffname(result.getName());//员工名称
            one.setUnionid(result.getUnionid());//员工员工在当前开发者企业账号范围内的唯一标识。
            one.setDingdingid(result.getUserid());//员工的userid。
            one.setJobnum(result.getJob_number());//员工工号。

            //根据部门id查询部门信息
            List<Department> departments = new ArrayList<>();

            for (Integer dept_id : result.getDept_id_listArray()) {//所属部门ID列表
                //请求钉钉获取部门信息
                Response<Department> fondDepartment = JdtDepartmentAPI.getDepartmentById(dept_id, accessToken);
            }

            one.setDepartment(departments.get(0).getName());
            one.setJob(result.getTitle());
            one.setPhone(result.getMobile());
            one.setIsfirstlogin(0);
        } else {//如果不是第一次，更改状态
            one.setIsfirstlogin(1);
        }
        boolean save = bonusStaffService.saveOrUpdate(one);
        return Result.OK("免登成功", one);
    }


    //密码操作
    @AutoLog(value = "微应用免密码操作接口日志")
    @ApiOperation(value = "微应用密码操作接口", notes = " (请求参数:jobNum,pas,type)   -- 微应用密码操作接口（1add，2login，3update） --结果集为true或flase")
    @PostMapping("/modifyPasOrLogin")
    Result modifyPasOrLogin(@RequestBody JSONObject jsonObject) {
        String jobNum = jsonObject.getString("jobNum");
        String pas = jsonObject.getString("pas");
        String type = jsonObject.getString("type");
        if (StringUtils.isAllEmpty(jobNum, pas, type)) return Result.error("参数有误");

        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("jobNum", jobNum);
        BonusStaff one = bonusStaffService.getOne(queryWrapper);
        if (one == null) {
            return Result.error(10002, "无员工信息");
        }

        Result<String> ok = new Result<>();
        ok.setSuccess(true);
        ok.setResult("操作成功");
        ok.setMessage("操作成功");
        ok.setCode(10000);
        if (type.equalsIgnoreCase("1")) {//设置密码
            one.setPas(pas);
            boolean b1 = bonusStaffService.updateById(one);
            if (!b1) return Result.error(10003, "设置密码失败");
            return Result.OK("设置密码操作成功");
        } else if (type.equalsIgnoreCase("2")) {//2.输入密码
            if (one.getPas().equals(pas)) {
                return Result.OK("登录操作成功");
            } else {
                return Result.error(10002, "密码错误");
            }

        } else if (type.equalsIgnoreCase("3")) {//3.重置密码
            one.setPas(pas);
            boolean b1 = bonusStaffService.updateById(one);
            if (!b1) return Result.error(10003, "重置密码失败");
            return Result.OK("重置密码操作成功");
        }

        return Result.error("操作参数有误");
    }

    //指定时间员工奖励金明细接口
    @AutoLog(value = "微应用指定时间查询奖励金接口日志")
    @ApiOperation(value = "微应用指定时间查询奖励金接口", notes = "(请求参数:jobNum,year,month)--结果集是bonus_staff对象（staffName，jobNum，department，job，phone，detailList），detailList包含多个bonus_detail对象（year，month，moneyAdd，moneyGive，moneyTotal）    --员工工号与年月查询一个员工信息与多个奖励金信息")
    @PostMapping("/bonusDetail")
    @ResponseBody
    Result bonusDetail(@RequestBody BonusDetail bonusDetail) {
        if (bonusDetail == null) return Result.error("参数有误");
        String jobNum = bonusDetail.getJobnum();
        Integer year = bonusDetail.getYear();
        Integer month = bonusDetail.getMonth();
        if (StringUtils.isEmpty(jobNum)) return Result.error("工号有误");

        BonusStaff one;
        try {
            QueryWrapper queryWrapper = new QueryWrapper();
            //查询员工名称，工号，部门，职务，手机号
            queryWrapper.select("staffName", "jobNum", "department", "job", "phone");
            //根据工号查询员工角色
            queryWrapper.eq("jobNum", jobNum);
            one = bonusStaffService.getOne(queryWrapper);

            //查询指定时间下的数据
//            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM");
//            Calendar calendar = Calendar.getInstance();
//            calendar.setTime(new Date());
//            calendar.add(Calendar.MONTH, -1);
//            String startDate = dateFormat.format(calendar.getTime());
//            calendar.add(Calendar.MONTH, -11);
//            String endDate = dateFormat.format(calendar.getTime());
//            queryWrapper.between("DATE_FORMAT(upTime,'%Y-%m-%d')", startDate, endDate);
            QueryWrapper detailWrapper = new QueryWrapper();

            //过滤查询结果
            detailWrapper.select("year", "month", "money_add", "money_give","money_total");
            //根据员工工号,年份，月份查询员工激励金
            detailWrapper.eq("jobNum", jobNum);
            detailWrapper.eq("year", year);
            detailWrapper.eq("month", month);
            //detailWrapper.orderByDesc("standardDate");
            List<BonusDetail> list = bonusDetailService.list(detailWrapper);
            //填充数据
            one.setDetailList(list);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(10004, "系统异常");
        }
        return Result.OK("查询成功", one);
    }

}
