package study.querydsl;

import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.QTeam;
import study.querydsl.entity.Team;

@SpringBootTest
@Transactional
public class QueryDslBasicTest {
    @PersistenceContext
    EntityManager em;

    @PersistenceContext
    EntityManagerFactory emf;

    JPAQueryFactory queryFactory;

    @BeforeEach
    public void testEntity() {
        queryFactory = new JPAQueryFactory(em);

        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test //member1을 찾아라.
    public void startJPQL() {
        String qlString =
            "select m from Member m " +
                "where m.username = :username";
        Member findMember = em.createQuery(qlString, Member.class)
            .setParameter("username", "member1")
            .getSingleResult();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test //member1을 찾아라.
    public void startQuerydsl() {
        QMember m = new QMember("m");

        Member findMember = queryFactory
            .select(m)
            .from(m)
            .where(m.username.eq("member1"))//파라미터 바인딩 처리
            .fetchOne();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test //member1을 찾아라.
    public void startQuerydsl2() {
        Member findMember = queryFactory
            .select(member)
            .from(member)
            .where(member.username.eq("member1"))//파라미터 바인딩 처리
            .fetchOne();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));
        List<Member> result = queryFactory
            .selectFrom(member)
            .where(member.age.eq(100))
            .orderBy(member.age.desc(), member.username.asc().nullsLast())
            .fetch();
        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);
        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();
    }

    @Test
    public void paging1() {
        List<Member> result = queryFactory
            .selectFrom(member)
            .orderBy(member.username.desc())
            .offset(1) //0부터 시작(zero index)
            .limit(2) //최대 2건 조회
            .fetch();
        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    public void paging2() {
        QueryResults<Member> queryResults = queryFactory
            .selectFrom(member)
            .orderBy(member.username.desc())
            .offset(1)
            .limit(2)
            .fetchResults();
        assertThat(queryResults.getTotal()).isEqualTo(4);
        assertThat(queryResults.getLimit()).isEqualTo(2);
        assertThat(queryResults.getOffset()).isEqualTo(1);
        assertThat(queryResults.getResults().size()).isEqualTo(2);
    }

    @Test
    public void aggregation() {
        List<Tuple> result = queryFactory
            .select(
                member.count(),
                member.age.sum(),
                member.age.avg(),
                member.age.min(),
                member.age.max()
            )
            .from(member)
            .fetch();

        Tuple tuple = result.get(0);
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }
    
    @Test
    public void group() throws Exception {
        List<Tuple> result = queryFactory
            .select(team.name, member.age.avg())
            .from(member)
            .join(member.team, team)
            .groupBy(team.name)
            .having(team.name.notIn("Test"))
            .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15);
        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35);
    }

    @Test
    public void join() throws Exception {
        QMember member = QMember.member;
        QTeam team = QTeam.team;

        List<Member> result = queryFactory
            .selectFrom(member)
            .join(member.team, team) // 조인
            .where(team.name.eq("teamA"))
            .fetch();

        assertThat(result)
            .extracting("username")
            .containsExactly("member1", "member2");
    }

    @Test
    public void theta_join() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        List<Member> result = queryFactory
            .select(member)
            .from(member, team)
            .where(member.username.eq(team.name))
            .fetch();

        assertThat(result)
            .extracting("username")
            .containsExactly("teamA", "teamB");
    }

    @Test
    public void join_on_filtering() throws Exception {
        List<Tuple> result = queryFactory
            .select(member, team)
            .from(member)
            .leftJoin(member.team, team).on(team.name.eq("teamA"))
            .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    public void join_on_no_relation() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        List<Tuple> result = queryFactory
            .select(member, team)
            .from(member)
            .leftJoin(team).on(member.username.eq(team.name))
            .fetch();

        for (Tuple tuple : result) {
            System.out.println("t=" + tuple);
        }
    }

    @Test
    public void fetchJoinUse() throws Exception {
        em.flush();
        em.clear();
        Member findMember = queryFactory
            .selectFrom(member)
            .join(member.team, team).fetchJoin()
            .where(member.username.eq("member1"))
            .fetchOne();

        boolean loaded =
            emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 적용").isTrue();
    }

    @Test
    public void subQuery() throws Exception {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
            .selectFrom(member)
            .where(member.age.eq(
                JPAExpressions // 서브쿼리
                    .select(memberSub.age.max())
                    .from(memberSub)
            ))
            .fetch();

        assertThat(result).extracting("age")
            .containsExactly(40);
    }

    @Test
    public void subQuery_select() throws Exception {
        QMember memberSub = new QMember("memberSub");

        List<Tuple> fetch = queryFactory
            .select(member.username,
                JPAExpressions
                    .select(memberSub.age.avg())
                    .from(memberSub)
            ).from(member)
            .fetch();

        for (Tuple tuple : fetch) {
            System.out.println("username = " + tuple.get(member.username));
            System.out.println("age = " +
                tuple.get(JPAExpressions.select(memberSub.age.avg())
                    .from(memberSub)));
        }
    }

    @Test
    public void case1() throws Exception {
        List<String> result = queryFactory
            .select(member.age
                .when(10).then("열살")
                .when(20).then("스무살")
                .otherwise("기타"))
            .from(member)
            .fetch();
    }

    @Test
    public void case2() throws Exception {
        List<String> result = queryFactory
            .select(new CaseBuilder()
                .when(member.age.between(0, 20)).then("0~20살")
                .when(member.age.between(21, 30)).then("21~30살")
                .otherwise("기타"))
            .from(member)
            .fetch();
    }

    @Test
    public void tupleTest() throws Exception {
        // 하나
        List<String> result = queryFactory
            .select(member.username)
            .from(member)
            .fetch();

        // 둘 이상
        List<Tuple> result2 = queryFactory
            .select(member.username, member.age)
            .from(member)
            .fetch();

        for (Tuple tuple : result2) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username=" + username);
            System.out.println("age=" + age);
        }
    }

    @Test
    public void dtoQuerydsl() throws Exception {
        List<MemberDto> result1 = queryFactory
            .select(Projections.bean(MemberDto.class,
                member.username,
                member.age))
            .from(member)
            .fetch();

        List<MemberDto> result2 = queryFactory
            .select(Projections.fields(MemberDto.class,
                member.username,
                member.age))
            .from(member)
            .fetch();

        List<MemberDto> result3 = queryFactory
            .select(Projections.constructor(MemberDto.class,
                member.username,
                member.age))
            .from(member)
            .fetch();
    }

    @Test
    public void findUserDto() throws Exception {
        QMember memberSub = new QMember("memberSub");
        queryFactory
            .select(Projections.fields(MemberDto.class,
                member.username.as("name"),
                ExpressionUtils.as(
                    JPAExpressions
                    .select(memberSub.age.max())
                    .from(memberSub), "age")
                )
            ).from(member)
            .fetch();
    }

    @Test
    public void BooleanBuilder() throws Exception {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = serchMember1(usernameParam, ageParam);
        Assertions.assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> serchMember1(String usernameParam, Integer ageParam) {
        BooleanBuilder builder = new BooleanBuilder();
        if (usernameParam != null)
            builder.and(member.username.eq(usernameParam));
        if (ageParam != null)
            builder.and(member.age.eq(ageParam));

        return queryFactory
            .selectFrom(member)
            .where(builder)
            .fetch();
    }

    @Test
    public List<Member> serchMember2(String usernameParam, Integer ageParam) throws Exception {
        return queryFactory
            .selectFrom(member)
            .where(usernameEq(usernameParam), ageEq(ageParam))
            .fetch();
    }

    private Predicate ageEq(Integer ageParam) {
        return ageParam != null ? member.age.eq(ageParam) : null;
    }

    private Predicate usernameEq(String usernameParam) {
        return usernameParam != null ? member.username.eq(usernameParam) : null;
    }

    @Test
    public void bulk() throws Exception {
        queryFactory
            .update(member)
            .set(member.username, "비회원")
            .where(member.age.lt(28))
            .execute();

        queryFactory
            .update(member)
            .set(member.age, member.age.add(1))
            .execute();

        queryFactory
            .delete(member)
            .where(member.age.gt(10))
            .execute();
    }

     @Test
     public void sqlFuction() throws Exception {
         queryFactory
             .select(Expressions.stringTemplate(
                 "function('replace', {0}, {1}, {2})",
                  member.username, "member", "M"))
             .from(member)
             .fetch();
     }
}
