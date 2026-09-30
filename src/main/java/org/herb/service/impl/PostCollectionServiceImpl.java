package org.herb.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.herb.mapper.PostCollectionMapper;
import org.herb.pojo.PageBean;
import org.herb.pojo.Post;
import org.herb.pojo.Prescription;
import org.herb.service.PostCollectionService;
import org.herb.utils.ThreadLocalUtil;
import org.herb.utils.CurrentUser;
import org.herb.exception.ForbiddenException;
import org.herb.exception.NotFoundException;
import org.herb.mapper.PostMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PostCollectionServiceImpl implements PostCollectionService {
    @Autowired
    private PostCollectionMapper postCollectionMapper;

    @Autowired
    private PostMapper postMapper;

    @Override
    @Transactional
    public void collect(Integer postId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        if (postCollectionMapper.isCollect(postId, userId) == null) {
            postCollectionMapper.collect(postId, userId);
            postCollectionMapper.addCollNum(postId);
        }
    }

    @Override
    public void addCollNum(Integer postId) {
        throw new ForbiddenException("请使用收藏接口");
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        Integer postId = postCollectionMapper.findOwnedPostId(id, CurrentUser.id());
        if (postId == null) throw new ForbiddenException("无权删除该收藏");
        if (postCollectionMapper.deleteOwned(id, CurrentUser.id()) == 1) {
            postCollectionMapper.subtractCollNum(postId);
        }
    }

    @Override
    public void subtractCollNum(Integer postId) {
        throw new ForbiddenException("请使用取消收藏接口");
    }

    @Override
    public Post isCollect(Integer postId, Integer userId) {
        CurrentUser.requireSelf(userId);
        return postCollectionMapper.isCollect(postId, userId);
    }

    @Override
    public PageBean<Post> list(Integer pageNum, Integer pageSize, Integer userId) {
        CurrentUser.requireSelf(userId);
        //创建pageBean对象封装查询好的对象
        PageBean<Post> pb = new PageBean<>();

        //开启分页查询 PageHelper
        PageHelper.startPage(pageNum, pageSize);

        //调用mapper
        List<Post> ps = postCollectionMapper.list(userId);

        //Page中提供了方法，可以获取PageHelper分页查询后得到的总记录条数和当前页数据，如果不强转，多态不允许父类去调用子类特有的方法
        Page<Post> p = (Page<Post>) ps;

        //把数据填充到PageBean对象中
        pb.setTotal(p.getTotal());
        pb.setItems(p.getResult());
        return pb;
    }

    @Override
    public void deleteByPostId(Integer postId) {
        Post post = postMapper.findById(postId);
        if (post == null) throw new NotFoundException("帖子不存在");
        CurrentUser.requireOwnerOrAdmin(post.getPosterId());
        postCollectionMapper.deleteByPostId(postId);
    }

    @Override
    public void deleteByUserId(Integer userId) {
        postCollectionMapper.deleteByUserId(userId);
    }
}
