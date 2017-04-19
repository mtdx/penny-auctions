package com.ninja.ninjabid.web.rest;

import com.ninja.ninjabid.NinjabidApp;

import com.ninja.ninjabid.domain.Credit;
import com.ninja.ninjabid.domain.User;
import com.ninja.ninjabid.repository.CreditRepository;
import com.ninja.ninjabid.service.CreditService;
import com.ninja.ninjabid.repository.search.CreditSearchRepository;
import com.ninja.ninjabid.service.dto.CreditDTO;
import com.ninja.ninjabid.service.mapper.CreditMapper;
import com.ninja.ninjabid.web.rest.errors.ExceptionTranslator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;

import static com.ninja.ninjabid.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ninja.ninjabid.domain.enumeration.Status;
/**
 * Test class for the CreditResource REST controller.
 *
 * @see CreditResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = NinjabidApp.class)
public class CreditResourceIntTest {

    private static final Integer DEFAULT_AMOUNT = 1;
    private static final Integer UPDATED_AMOUNT = 2;

    private static final Status DEFAULT_STATUS = Status.paid;
    private static final Status UPDATED_STATUS = Status.pending;

    private static final Double DEFAULT_PRICE = 0.1D;
    private static final Double UPDATED_PRICE = 1D;

    private static final ZonedDateTime DEFAULT_TIMESTAMP = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_TIMESTAMP = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

    @Autowired
    private CreditRepository creditRepository;

    @Autowired
    private CreditMapper creditMapper;

    @Autowired
    private CreditService creditService;

    @Autowired
    private CreditSearchRepository creditSearchRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restCreditMockMvc;

    private Credit credit;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        CreditResource creditResource = new CreditResource(creditService);
        this.restCreditMockMvc = MockMvcBuilders.standaloneSetup(creditResource)
            .setCustomArgumentResolvers(pageableArgumentResolver)
            .setControllerAdvice(exceptionTranslator)
            .setMessageConverters(jacksonMessageConverter).build();
    }

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Credit createEntity(EntityManager em) {
        Credit credit = new Credit()
            .amount(DEFAULT_AMOUNT)
            .status(DEFAULT_STATUS)
            .price(DEFAULT_PRICE)
            .timestamp(DEFAULT_TIMESTAMP);
        // Add required entity
        User user = UserResourceIntTest.createEntity(em);
        em.persist(user);
        em.flush();
        credit.setUser(user);
        return credit;
    }

    @Before
    public void initTest() {
        creditSearchRepository.deleteAll();
        credit = createEntity(em);
    }

    @Test
    @Transactional
    public void createCredit() throws Exception {
        int databaseSizeBeforeCreate = creditRepository.findAll().size();

        // Create the Credit
        CreditDTO creditDTO = creditMapper.creditToCreditDTO(credit);
        restCreditMockMvc.perform(post("/api/credits")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(creditDTO)))
            .andExpect(status().isCreated());

        // Validate the Credit in the database
        List<Credit> creditList = creditRepository.findAll();
        assertThat(creditList).hasSize(databaseSizeBeforeCreate + 1);
        Credit testCredit = creditList.get(creditList.size() - 1);
        assertThat(testCredit.getAmount()).isEqualTo(DEFAULT_AMOUNT);
        assertThat(testCredit.getStatus()).isEqualTo(DEFAULT_STATUS);
        assertThat(testCredit.getPrice()).isEqualTo(DEFAULT_PRICE);
        assertThat(testCredit.getTimestamp()).isEqualTo(DEFAULT_TIMESTAMP);

        // Validate the Credit in Elasticsearch
        Credit creditEs = creditSearchRepository.findOne(testCredit.getId());
        assertThat(creditEs).isEqualToComparingFieldByField(testCredit);
    }

    @Test
    @Transactional
    public void createCreditWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = creditRepository.findAll().size();

        // Create the Credit with an existing ID
        credit.setId(1L);
        CreditDTO creditDTO = creditMapper.creditToCreditDTO(credit);

        // An entity with an existing ID cannot be created, so this API call must fail
        restCreditMockMvc.perform(post("/api/credits")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(creditDTO)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<Credit> creditList = creditRepository.findAll();
        assertThat(creditList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void checkAmountIsRequired() throws Exception {
        int databaseSizeBeforeTest = creditRepository.findAll().size();
        // set the field null
        credit.setAmount(null);

        // Create the Credit, which fails.
        CreditDTO creditDTO = creditMapper.creditToCreditDTO(credit);

        restCreditMockMvc.perform(post("/api/credits")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(creditDTO)))
            .andExpect(status().isBadRequest());

        List<Credit> creditList = creditRepository.findAll();
        assertThat(creditList).hasSize(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    public void checkStatusIsRequired() throws Exception {
        int databaseSizeBeforeTest = creditRepository.findAll().size();
        // set the field null
        credit.setStatus(null);

        // Create the Credit, which fails.
        CreditDTO creditDTO = creditMapper.creditToCreditDTO(credit);

        restCreditMockMvc.perform(post("/api/credits")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(creditDTO)))
            .andExpect(status().isBadRequest());

        List<Credit> creditList = creditRepository.findAll();
        assertThat(creditList).hasSize(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    public void checkPriceIsRequired() throws Exception {
        int databaseSizeBeforeTest = creditRepository.findAll().size();
        // set the field null
        credit.setPrice(null);

        // Create the Credit, which fails.
        CreditDTO creditDTO = creditMapper.creditToCreditDTO(credit);

        restCreditMockMvc.perform(post("/api/credits")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(creditDTO)))
            .andExpect(status().isBadRequest());

        List<Credit> creditList = creditRepository.findAll();
        assertThat(creditList).hasSize(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    public void checkTimestampIsRequired() throws Exception {
        int databaseSizeBeforeTest = creditRepository.findAll().size();
        // set the field null
        credit.setTimestamp(null);

        // Create the Credit, which fails.
        CreditDTO creditDTO = creditMapper.creditToCreditDTO(credit);

        restCreditMockMvc.perform(post("/api/credits")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(creditDTO)))
            .andExpect(status().isBadRequest());

        List<Credit> creditList = creditRepository.findAll();
        assertThat(creditList).hasSize(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    public void getAllCredits() throws Exception {
        // Initialize the database
        creditRepository.saveAndFlush(credit);

        // Get all the creditList
        restCreditMockMvc.perform(get("/api/credits?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(credit.getId().intValue())))
            .andExpect(jsonPath("$.[*].amount").value(hasItem(DEFAULT_AMOUNT)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].price").value(hasItem(DEFAULT_PRICE.doubleValue())))
            .andExpect(jsonPath("$.[*].timestamp").value(hasItem(sameInstant(DEFAULT_TIMESTAMP))));
    }

    @Test
    @Transactional
    public void getCredit() throws Exception {
        // Initialize the database
        creditRepository.saveAndFlush(credit);

        // Get the credit
        restCreditMockMvc.perform(get("/api/credits/{id}", credit.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(credit.getId().intValue()))
            .andExpect(jsonPath("$.amount").value(DEFAULT_AMOUNT))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()))
            .andExpect(jsonPath("$.price").value(DEFAULT_PRICE.doubleValue()))
            .andExpect(jsonPath("$.timestamp").value(sameInstant(DEFAULT_TIMESTAMP)));
    }

    @Test
    @Transactional
    public void getNonExistingCredit() throws Exception {
        // Get the credit
        restCreditMockMvc.perform(get("/api/credits/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateCredit() throws Exception {
        // Initialize the database
        creditRepository.saveAndFlush(credit);
        creditSearchRepository.save(credit);
        int databaseSizeBeforeUpdate = creditRepository.findAll().size();

        // Update the credit
        Credit updatedCredit = creditRepository.findOne(credit.getId());
        updatedCredit
            .amount(UPDATED_AMOUNT)
            .status(UPDATED_STATUS)
            .price(UPDATED_PRICE)
            .timestamp(UPDATED_TIMESTAMP);
        CreditDTO creditDTO = creditMapper.creditToCreditDTO(updatedCredit);

        restCreditMockMvc.perform(put("/api/credits")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(creditDTO)))
            .andExpect(status().isOk());

        // Validate the Credit in the database
        List<Credit> creditList = creditRepository.findAll();
        assertThat(creditList).hasSize(databaseSizeBeforeUpdate);
        Credit testCredit = creditList.get(creditList.size() - 1);
        assertThat(testCredit.getAmount()).isEqualTo(UPDATED_AMOUNT);
        assertThat(testCredit.getStatus()).isEqualTo(UPDATED_STATUS);
        assertThat(testCredit.getPrice()).isEqualTo(UPDATED_PRICE);
        assertThat(testCredit.getTimestamp()).isEqualTo(UPDATED_TIMESTAMP);

        // Validate the Credit in Elasticsearch
        Credit creditEs = creditSearchRepository.findOne(testCredit.getId());
        assertThat(creditEs).isEqualToComparingFieldByField(testCredit);
    }

    @Test
    @Transactional
    public void updateNonExistingCredit() throws Exception {
        int databaseSizeBeforeUpdate = creditRepository.findAll().size();

        // Create the Credit
        CreditDTO creditDTO = creditMapper.creditToCreditDTO(credit);

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restCreditMockMvc.perform(put("/api/credits")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(creditDTO)))
            .andExpect(status().isCreated());

        // Validate the Credit in the database
        List<Credit> creditList = creditRepository.findAll();
        assertThat(creditList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteCredit() throws Exception {
        // Initialize the database
        creditRepository.saveAndFlush(credit);
        creditSearchRepository.save(credit);
        int databaseSizeBeforeDelete = creditRepository.findAll().size();

        // Get the credit
        restCreditMockMvc.perform(delete("/api/credits/{id}", credit.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate Elasticsearch is empty
        boolean creditExistsInEs = creditSearchRepository.exists(credit.getId());
        assertThat(creditExistsInEs).isFalse();

        // Validate the database is empty
        List<Credit> creditList = creditRepository.findAll();
        assertThat(creditList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void searchCredit() throws Exception {
        // Initialize the database
        creditRepository.saveAndFlush(credit);
        creditSearchRepository.save(credit);

        // Search the credit
        restCreditMockMvc.perform(get("/api/_search/credits?query=id:" + credit.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(credit.getId().intValue())))
            .andExpect(jsonPath("$.[*].amount").value(hasItem(DEFAULT_AMOUNT)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].price").value(hasItem(DEFAULT_PRICE.doubleValue())))
            .andExpect(jsonPath("$.[*].timestamp").value(hasItem(sameInstant(DEFAULT_TIMESTAMP))));
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Credit.class);
    }
}
