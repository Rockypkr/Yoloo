package com.yoloo.backend.bookmark;

import com.google.api.server.spi.response.ConflictException;
import com.google.appengine.api.datastore.Email;
import com.google.appengine.api.datastore.Link;
import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Ref;
import com.yoloo.backend.account.Account;
import com.yoloo.backend.account.AccountShard;
import com.yoloo.backend.account.AccountEntity;
import com.yoloo.backend.account.AccountShardService;
import com.yoloo.backend.category.Category;
import com.yoloo.backend.category.CategoryController;
import com.yoloo.backend.category.CategoryControllerFactory;
import com.yoloo.backend.device.DeviceRecord;
import com.yoloo.backend.game.GamificationService;
import com.yoloo.backend.game.Tracker;
import com.yoloo.backend.post.Post;
import com.yoloo.backend.post.PostController;
import com.yoloo.backend.post.PostControllerFactory;
import com.yoloo.backend.tag.Tag;
import com.yoloo.backend.tag.TagController;
import com.yoloo.backend.tag.TagControllerFactory;
import com.yoloo.backend.util.TestBase;
import io.reactivex.Observable;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;
import org.junit.Test;

import static com.yoloo.backend.util.TestObjectifyService.fact;
import static com.yoloo.backend.util.TestObjectifyService.ofy;
import static org.junit.Assert.assertEquals;

public class BookmarkControllerTest extends TestBase {

  private static final String USER_EMAIL = "test@gmail.com";
  private static final String USER_AUTH_DOMAIN = "gmail.com";

  private Post post;

  private PostController postController;
  private BookmarkController bookmarkController;
  private TagController tagController;
  private CategoryController categoryController;

  @Override
  public void setUpGAE() {
    super.setUpGAE();

    helper.setEnvIsLoggedIn(true)
        .setEnvIsAdmin(true)
        .setEnvAuthDomain(USER_AUTH_DOMAIN)
        .setEnvEmail(USER_EMAIL);
  }

  @Override
  public void setUp() {
    super.setUp();

    postController = PostControllerFactory.of().create();
    bookmarkController = BookmarkControllerFactory.of().create();
    tagController = TagControllerFactory.of().create();
    categoryController = CategoryControllerFactory.of().create();

    AccountEntity model = createAccount();

    Account owner = model.getAccount();
    DeviceRecord record = createRecord(owner);
    Tracker tracker = GamificationService.create().createTracker(owner.getKey());

    User user = new User(USER_EMAIL, USER_AUTH_DOMAIN, owner.getWebsafeId());

    Category europe = null;
    try {
      europe = categoryController.insertCategory("europe", Category.Type.THEME);
    } catch (ConflictException e) {
      e.printStackTrace();
    }

    Tag passport = tagController.insertGroup("passport");

    Tag visa = tagController.insertTag("visa", "en", passport.getWebsafeId());

    ImmutableSet<Object> saveList = ImmutableSet.builder()
        .add(owner)
        .addAll(model.getShards().values())
        .add(tracker)
        .add(europe)
        .add(passport)
        .add(visa)
        .add(record)
        .build();

    ofy().save().entities(saveList).now();

    post =
        postController.insertQuestion("Test content", "visa,passport", "europe", Optional.absent(),
            Optional.absent(), user);
  }

  @Test
  public void testSaveQuestion() throws Exception {
    final User user = UserServiceFactory.getUserService().getCurrentUser();

    bookmarkController.insertBookmark(post.getWebsafeId(), user);

    List<Bookmark> bookmarks = ofy().load().type(Bookmark.class)
        .ancestor(Key.<Account>create(user.getUserId()))
        .list();

    assertEquals(1, bookmarks.size());
  }

  @Test
  public void testUnSaveQuestion() throws Exception {
    final User user = UserServiceFactory.getUserService().getCurrentUser();

    bookmarkController.insertBookmark(post.getWebsafeId(), user);

    List<Bookmark> bookmarks1 = ofy().load().type(Bookmark.class)
        .ancestor(Key.<Account>create(user.getUserId()))
        .list();

    assertEquals(1, bookmarks1.size());

    bookmarkController.deleteBookmark(post.getWebsafeId(), user);

    List<Bookmark> bookmarks2 = ofy().load().type(Bookmark.class)
        .ancestor(Key.<Account>create(user.getUserId()))
        .list();

    assertEquals(0, bookmarks2.size());
  }

  @Test
  public void testListSavedQuestions() throws Exception {
    final User user = UserServiceFactory.getUserService().getCurrentUser();

    Post post2 =
        postController.insertQuestion("Test content", "visa,passport", "europe", Optional.absent(),
            Optional.absent(), user);

    bookmarkController.insertBookmark(post.getWebsafeId(), user);
    bookmarkController.insertBookmark(post2.getWebsafeId(), user);

    List<Bookmark> bookmarks = ofy().load().type(Bookmark.class)
        .ancestor(Key.<Account>create(user.getUserId()))
        .list();

    assertEquals(2, bookmarks.size());
  }

  private AccountEntity createAccount() {
    final Key<Account> ownerKey = fact().allocateId(Account.class);

    AccountShardService ass = AccountShardService.create();

    return Observable.range(1, AccountShard.SHARD_COUNT)
        .map(shardNum -> ass.createShard(ownerKey, shardNum))
        .toMap(Ref::create)
        .map(shardMap -> {
          Account account = Account.builder()
              .id(ownerKey.getId())
              .avatarUrl(new Link("Test avatar"))
              .email(new Email(USER_EMAIL))
              .username("Test user")
              .shardRefs(Lists.newArrayList(shardMap.keySet()))
              .created(DateTime.now())
              .build();

          return AccountEntity.builder()
              .account(account)
              .shards(shardMap)
              .build();
        })
        .blockingGet();
  }

  private DeviceRecord createRecord(Account owner) {
    return DeviceRecord.builder()
        .id(owner.getWebsafeId())
        .parentUserKey(owner.getKey())
        .regId(UUID.randomUUID().toString())
        .build();
  }
}