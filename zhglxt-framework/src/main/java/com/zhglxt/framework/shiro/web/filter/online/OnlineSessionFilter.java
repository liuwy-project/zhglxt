package com.zhglxt.framework.shiro.web.filter.online;

import com.zhglxt.common.config.GlobalConfig;
import com.zhglxt.common.constant.ShiroConstants;
import com.zhglxt.common.core.entity.sys.SysRole;
import com.zhglxt.common.core.entity.sys.SysUser;
import com.zhglxt.common.enums.OnlineStatus;
import com.zhglxt.common.utils.ShiroUtils;
import com.zhglxt.common.utils.StringUtils;
import com.zhglxt.framework.shiro.session.OnlineSession;
import com.zhglxt.framework.shiro.session.OnlineSessionDAO;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.AccessControlFilter;
import org.apache.shiro.web.util.WebUtils;
import org.springframework.beans.factory.annotation.Value;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 自定义访问控制
 * 
 * @author ruoyi
 */
public class OnlineSessionFilter extends AccessControlFilter
{
    /**
     * 强制退出后重定向的地址
     */
    @Value("${shiro.user.loginUrl}")
    private String loginUrl;

    private OnlineSessionDAO onlineSessionDAO;

    /**
     * 表示是否允许访问；mappedValue就是[urls]配置中拦截器参数部分，如果允许访问返回true，否则false；
     */
    @Override
    protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue)
            throws Exception
    {
        Subject subject = getSubject(request, response);
        if (subject == null || subject.getSession() == null)
        {
            return true;
        }
        Session session = onlineSessionDAO.readSession(subject.getSession().getId());
        if (session != null && session instanceof OnlineSession)
        {
            OnlineSession onlineSession = (OnlineSession) session;
            request.setAttribute(ShiroConstants.ONLINE_SESSION, onlineSession);
            // 把user对象设置进去
            boolean isGuest = StringUtils.isEmpty(onlineSession.getUserId()) || "0".equals(onlineSession.getUserId());
            if (isGuest == true)
            {
                SysUser user = ShiroUtils.getSysUser();
                if (user != null)
                {
                    onlineSession.setUserId(user.getUserId());
                    onlineSession.setLoginName(user.getLoginName());
                    onlineSession.setAvatar(user.getAvatar());
                    onlineSession.setDeptName(user.getDept().getDeptName());
                    onlineSession.markAttributeChanged();
                    onlineSessionDAO.update(onlineSession);

                    List<String> lists = new ArrayList<>();
                    //获取当前用户的所有角色
                    List<SysRole> roles = user.getRoles();
                    if (roles.size() > 0) {
                        for (int i = 0; i < roles.size(); i++) {
                            //保存角色key到list中
                            lists.add(roles.get(i).getRoleKey().trim());
                        }
                    }

                    //CKFinder文件权限控制（例如：用户拥有：超级管理员角色（sys）并且不是演示模式时，文件才能进行上传、修改、删除等操作）

                    String roleFlag = "";
                    /*
                    if(lists.contains("sys")){//超级系统管理员
                        roleFlag="sys";
                    }else if(lists.contains("admin")){//管理员
                        roleFlag="admin";
                    }
                    */

                    //是否演示模式
                    if (!GlobalConfig.isDemoEnabled()) {
                        roleFlag = "sys";
                    }

                    //演示模式不允许文件上传、删除、修改等操作
                    //对应ckfinder.xml文件中的 <userRoleSessionVar>CKFinder_UserRole</userRoleSessionVar>
                    onlineSession.setAttribute("CKFinder_UserRole", roleFlag);

                    onlineSession.markAttributeChanged();
                }
            }

            if (onlineSession.getStatus() == OnlineStatus.off_line)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * 表示当访问拒绝时是否已经处理了；如果返回true表示需要继续处理；如果返回false表示该拦截器实例已经处理了，将直接返回即可。
     */
    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception
    {
        Subject subject = getSubject(request, response);
        if (subject != null)
        {
            subject.logout();
        }
        saveRequestAndRedirectToLogin(request, response);
        return false;
    }

    /**
     * 跳转到登录页
     */
    @Override
    protected void redirectToLogin(ServletRequest request, ServletResponse response) throws IOException
    {
        WebUtils.issueRedirect(request, response, loginUrl);
    }

    public void setOnlineSessionDAO(OnlineSessionDAO onlineSessionDAO)
    {
        this.onlineSessionDAO = onlineSessionDAO;
    }
}
