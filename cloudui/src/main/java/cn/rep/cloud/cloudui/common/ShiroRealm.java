package cn.rep.cloud.cloudui.common;

import cn.rep.cloud.cloudui.feignclient.IRepEmployeeServiceClient;
import cn.rep.cloud.cloudui.feignclient.dto.RepEmployeeClientDTO;
import cn.rep.cloud.cloudui.feignclient.vo.RepEmployeeClientVO;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.crypto.hash.SimpleHash;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.util.ByteSource;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

@Configuration
public class ShiroRealm extends AuthorizingRealm {

    @Resource
    private IRepEmployeeServiceClient iRepEmployeeServiceClient;

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token)throws AuthenticationException {
        getMd5();
        // 1. 把 AuthenticationToken 拆箱转换为 UsernamePasswordToken
        UsernamePasswordToken upToken = (UsernamePasswordToken) token;

        // 2. 从 UsernamePasswordToken 中来获取 username
        String username = upToken.getUsername();

        /*
         * 3. 调用数据库的方法, 从数据库中查询 username对应的用户记录
         *    注:一般的 , 用户名  什么的   最好唯一
         * 4. 若用户不存在, 则可以抛出 UnknownAccountException 异常
         * 5. 根据用户信息的情况, 决定是否需要抛出其他的 AuthenticationException 异常.
         * 6. principal:认证的用户实体信息.(可以为 username、手机号、邮箱等，也可以是一个携带用户信息的对象模型)
         *  注:也可以是数据表对应的用户的实体类对象,在鉴权时可以冲这个对象中回去到其对应有哪些权限.
         *  注:用户对象信息本应该是从数据库中查询出来的,这里为了快速测试，直接new一个
         *  注:用于存放用户信息的模型,必须能后实例化。即:必须实现Serializable接口
         *  注: 这里假设从数据库查出来了某个用户的数据,假设User类的实例principal中的就是查出来的数据
         */
        RepEmployeeClientDTO dto = new RepEmployeeClientDTO();
        dto.setLoginName(username);
        RepEmployeeClientVO principal = iRepEmployeeServiceClient.getRec(dto);
//        if(username != null && username.equals("admin")) {
//            principal.setUsername(username);
//            principal.setPassword("038bdaf98f2037b31f1e75b5b4c9b26e");
//        }
//        if(username != null && username.equals("a123")) {
//            principal.setUsername(username);
//            principal.setPassword("8c702ae443795331c91cfab48f3f3833");
//        }

        if (null == principal){
            return new SimpleAuthenticationInfo();
        }

        /*
         *  7.credentials: 凭证(一般都是密码).
         *    credentials本应该是查询出来的;这里为了快速测试,我们直接写
         */
        Object credentials = principal.getPassword();

        /*
         *  8.realmName: 当前 realm 对象的 name. 调用父类的 getName() 方法即可
         *    这里获取到的是:com.aspire.shiro.realms.ShiroRealm_0
         */
        String realmName = getName();

        /*
         *  9. 盐值.
         *  注:如果多个用户的密码一样，那么一般情况下加密结果也一样;
         *  注:通过使用不同的盐值来确保即便密码都一样,加密结果也会不一样
         *  注:盐值 最好保证其唯一性。
         *  注:由于一般情况下,用户名是唯一的，所以我们一般使用用户名来计算盐值
         */
        ByteSource credentialsSalt = ByteSource.Util.bytes(username);

        /*
         * 实例化对象.
         * 注意:如果不加密,那么就是直接比对的明文
         * 注意:SimpleAuthenticationInfo加密,是指:将用户登录时输入的密码(盐值)加密后,与数据库取出来的密码进行比对
         *     所以,这里的加密并不是对从数据库取出来的credentials进行加密!从数据库取出来的credentials应该是
         *     之前录入数据库时已经加密好了的.
         */
        SimpleAuthenticationInfo info = null;
        info = new SimpleAuthenticationInfo("", credentials, credentialsSalt, realmName);
        principal.setPassword("");
        return info;
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        System.out.println("...................ShiroRealm鉴权");
        /*
         * 1.从principals中获取到第一个principal
         *  注:多个Realm时,Shiro返回给此方法的参数principals的规则是由身份认证策略控制的(可详见:身份认证策略)
         *  注:默认的策略为AtLeastOneSuccessfulStrategy,那么这里只能获取
         *     到对应的通过了身份认证的Realm中的principal
         *  注:就算有多个Realm,一般而言这些Realm中的principal都应该是一样的,所以
         *     我们读取信息时，一般拿其中的某一个进行读取就行
         */
        Object principal = principals.getPrimaryPrincipal();

        /*
         * 2.从配置applicationContext.xml中可知:我们写的ShiroReaml是第一个,
         *  而ShiroReaml中的principal,我们传的是User对象
         *  所以这里获取到的principal即为该User对象,并获取到对应其角色信息
         */
        //User user = (User)principal;
        // 获取该用户(带的)角色信息
        String[] roles = null;
//        if(user.getUserRoles() != null) {
//            roles = (user.getUserRoles()).split(",");
//        }

        // 3.创建一个Set,来放置用户拥有的角色
        java.util.Set<String> rolesSet = new java.util.HashSet<>();
        for (String role : roles) {
            rolesSet.add(role);
        }

        // 4.创建 SimpleAuthorizationInfo, 并将办好角色的Set放入.
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo(rolesSet);

        // 5.返回 SimpleAuthorizationInfo对象.
        return info;
    }

    /**
     * 我们可以这样获取:盐值加密后的结果
     *
     * @param
     * @date 2018年8月15日 上午11:46:23
     */
    public void getMd5() {
        // 加密算法(MD5、SHA1等)
        String hashAlgorithmName = "MD5";
        // 加密对象(即:密码)
        Object credentials = "123456";
        // 盐值(注:一般使用用户名)
        Object salt = ByteSource.Util.bytes("8888");
        // 迭代次数
        int hashIterations = 1024;

        // 执行加密
        Object result = new SimpleHash(hashAlgorithmName, credentials, salt, hashIterations);
        System.out.println(result);
    }
}
