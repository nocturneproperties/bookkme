package com.bookk.business.domain.impl.operation.demo

import com.bookk.business.domain.api.client.entity.Client
import com.bookk.core.domain.entity.DemoBusinessKind
import com.bookk.core.domain.entity.Language
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

internal object DemoBusinessSample {

    fun of(kind: DemoBusinessKind, language: Language): SampleBusiness {
        val template = templateOf(kind)
        return SampleBusiness(
            name = template.name.of(language),
            currency = currencyOf(language),
            groups = template.groups.map { group ->
                SampleGroup(
                    name = group.name.of(language),
                    services = group.services.map { SampleService(it.name.of(language), it.duration, it.priceInEuro) },
                )
            },
            clients = clientsOf(language),
        )
    }

    private fun templateOf(kind: DemoBusinessKind): KindTemplate {
        return when (kind) {
            DemoBusinessKind.GENERAL -> general
            DemoBusinessKind.BEAUTY -> beauty
            DemoBusinessKind.FITNESS -> fitness
            DemoBusinessKind.TUTORING -> tutoring
            DemoBusinessKind.HEALTH -> health
        }
    }

    private fun currencyOf(language: Language): SampleCurrency {
        return when (language) {
            Language.EN -> SampleCurrency(CurrencyUnit.EUR, perEuro = 1)
            Language.UK -> SampleCurrency(CurrencyUnit.of("UAH"), perEuro = 40)
            Language.PL -> SampleCurrency(CurrencyUnit.of("PLN"), perEuro = 4)
        }
    }

    private fun clientsOf(language: Language): List<SampleClient> {
        return when (language) {
            Language.EN -> englishClients
            Language.UK -> ukrainianClients
            Language.PL -> polishClients
        }
    }

    private val general = KindTemplate(
        name = Text("Sample Studio", uk = "Зразкова студія", pl = "Przykładowe Studio"),
        groups = listOf(
            GroupTemplate(
                name = Text("Consultations", uk = "Консультації", pl = "Konsultacje"),
                services = listOf(
                    ServiceTemplate(
                        Text("Initial consultation", uk = "Первинна консультація", pl = "Pierwsza konsultacja"),
                        30.minutes,
                        priceInEuro = 25,
                    ),
                    ServiceTemplate(
                        Text("Follow-up consultation", uk = "Повторна консультація", pl = "Konsultacja kontrolna"),
                        30.minutes,
                        priceInEuro = 20,
                    ),
                ),
            ),
            GroupTemplate(
                name = Text("Sessions", uk = "Сеанси", pl = "Sesje"),
                services = listOf(
                    ServiceTemplate(
                        Text("Short session", uk = "Короткий сеанс", pl = "Krótka sesja"),
                        30.minutes,
                        priceInEuro = 20,
                    ),
                    ServiceTemplate(
                        Text("Standard session", uk = "Стандартний сеанс", pl = "Sesja standardowa"),
                        60.minutes,
                        priceInEuro = 40,
                    ),
                ),
            ),
        ),
    )

    private val beauty = KindTemplate(
        name = Text("Sample Beauty Studio", uk = "Зразкова студія краси", pl = "Przykładowe Studio Urody"),
        groups = listOf(
            GroupTemplate(
                name = Text("Hair", uk = "Волосся", pl = "Włosy"),
                services = listOf(
                    ServiceTemplate(Text("Haircut", uk = "Стрижка", pl = "Strzyżenie"), 45.minutes, priceInEuro = 30),
                    ServiceTemplate(
                        Text("Beard trim", uk = "Корекція бороди", pl = "Strzyżenie brody"),
                        30.minutes,
                        priceInEuro = 20,
                    ),
                ),
            ),
            GroupTemplate(
                name = Text("Nails", uk = "Нігті", pl = "Paznokcie"),
                services = listOf(
                    ServiceTemplate(Text("Manicure", uk = "Манікюр", pl = "Manicure"), 60.minutes, priceInEuro = 35),
                    ServiceTemplate(Text("Pedicure", uk = "Педикюр", pl = "Pedicure"), 60.minutes, priceInEuro = 40),
                ),
            ),
        ),
    )

    private val fitness = KindTemplate(
        name = Text("Sample Fitness Studio", uk = "Зразкова фітнес-студія", pl = "Przykładowe Studio Fitness"),
        groups = listOf(
            GroupTemplate(
                name = Text("Training", uk = "Тренування", pl = "Treningi"),
                services = listOf(
                    ServiceTemplate(
                        Text("Personal training", uk = "Персональне тренування", pl = "Trening personalny"),
                        60.minutes,
                        priceInEuro = 35,
                    ),
                    ServiceTemplate(
                        Text("Stretching", uk = "Стретчинг", pl = "Stretching"),
                        45.minutes,
                        priceInEuro = 20,
                    ),
                ),
            ),
            GroupTemplate(
                name = Text("Wellness", uk = "Відновлення", pl = "Regeneracja"),
                services = listOf(
                    ServiceTemplate(Text("Yoga", uk = "Йога", pl = "Joga"), 60.minutes, priceInEuro = 15),
                    ServiceTemplate(Text("Massage", uk = "Масаж", pl = "Masaż"), 60.minutes, priceInEuro = 45),
                ),
            ),
        ),
    )

    private val tutoring = KindTemplate(
        name = Text("Sample Learning Centre", uk = "Зразковий навчальний центр", pl = "Przykładowe Centrum Nauki"),
        groups = listOf(
            GroupTemplate(
                name = Text("Languages", uk = "Мови", pl = "Języki"),
                services = listOf(
                    ServiceTemplate(
                        Text("English lesson", uk = "Урок англійської", pl = "Lekcja angielskiego"),
                        60.minutes,
                        priceInEuro = 25,
                    ),
                    ServiceTemplate(
                        Text("Trial lesson", uk = "Пробний урок", pl = "Lekcja próbna"),
                        30.minutes,
                        priceInEuro = 10,
                    ),
                ),
            ),
            GroupTemplate(
                name = Text("School subjects", uk = "Шкільні предмети", pl = "Przedmioty szkolne"),
                services = listOf(
                    ServiceTemplate(
                        Text("Maths tutoring", uk = "Заняття з математики", pl = "Korepetycje z matematyki"),
                        60.minutes,
                        priceInEuro = 25,
                    ),
                    ServiceTemplate(
                        Text("Exam preparation", uk = "Підготовка до іспиту", pl = "Przygotowanie do egzaminu"),
                        60.minutes,
                        priceInEuro = 30,
                    ),
                ),
            ),
        ),
    )

    private val health = KindTemplate(
        name = Text("Sample Health Practice", uk = "Зразковий медичний кабінет", pl = "Przykładowy Gabinet"),
        groups = listOf(
            GroupTemplate(
                name = Text("Visits", uk = "Прийоми", pl = "Wizyty"),
                services = listOf(
                    ServiceTemplate(
                        Text("First visit", uk = "Первинний прийом", pl = "Pierwsza wizyta"),
                        45.minutes,
                        priceInEuro = 50,
                    ),
                    ServiceTemplate(
                        Text("Follow-up visit", uk = "Повторний прийом", pl = "Wizyta kontrolna"),
                        30.minutes,
                        priceInEuro = 35,
                    ),
                ),
            ),
            GroupTemplate(
                name = Text("Therapy", uk = "Терапія", pl = "Terapia"),
                services = listOf(
                    ServiceTemplate(
                        Text("Physiotherapy session", uk = "Сеанс фізіотерапії", pl = "Sesja fizjoterapii"),
                        60.minutes,
                        priceInEuro = 45,
                    ),
                    ServiceTemplate(
                        Text(
                            "Counselling session",
                            uk = "Психологічна консультація",
                            pl = "Konsultacja psychologiczna",
                        ),
                        60.minutes,
                        priceInEuro = 50,
                    ),
                ),
            ),
        ),
    )

    private val englishClients = listOf(
        SampleClient("Alex", "Example", "alex.example"),
        SampleClient("Bella", "Sample", "bella.sample"),
        SampleClient("Chris", "Demo", "chris.demo"),
        SampleClient("Dana", "Placeholder", "dana.placeholder"),
        SampleClient("Evan", "Testperson", "evan.testperson"),
        SampleClient("Fiona", "Fictional", "fiona.fictional"),
        SampleClient("Gabe", "Pretend", "gabe.pretend"),
        SampleClient("Hana", "Madeup", "hana.madeup"),
        SampleClient("Ivan", "Imaginary", "ivan.imaginary"),
        SampleClient("Jules", "Mockup", "jules.mockup"),
    )

    private val ukrainianClients = listOf(
        SampleClient("Олена", "Коваленко", "olena.kovalenko"),
        SampleClient("Андрій", "Бондаренко", "andrii.bondarenko"),
        SampleClient("Ірина", "Ткаченко", "iryna.tkachenko"),
        SampleClient("Дмитро", "Мельник", "dmytro.melnyk"),
        SampleClient("Наталія", "Кравченко", "nataliia.kravchenko"),
        SampleClient("Олександр", "Шевчук", "oleksandr.shevchuk"),
        SampleClient("Марія", "Поліщук", "mariia.polishchuk"),
        SampleClient("Сергій", "Бойко", "serhii.boiko"),
        SampleClient("Юлія", "Савченко", "yuliia.savchenko"),
        SampleClient("Максим", "Лисенко", "maksym.lysenko"),
    )

    private val polishClients = listOf(
        SampleClient("Anna", "Kowalska", "anna.kowalska"),
        SampleClient("Piotr", "Nowak", "piotr.nowak"),
        SampleClient("Katarzyna", "Wiśniewska", "katarzyna.wisniewska"),
        SampleClient("Tomasz", "Wójcik", "tomasz.wojcik"),
        SampleClient("Magdalena", "Kamińska", "magdalena.kaminska"),
        SampleClient("Michał", "Lewandowski", "michal.lewandowski"),
        SampleClient("Agnieszka", "Zielińska", "agnieszka.zielinska"),
        SampleClient("Paweł", "Szymański", "pawel.szymanski"),
        SampleClient("Joanna", "Woźniak", "joanna.wozniak"),
        SampleClient("Krzysztof", "Dąbrowski", "krzysztof.dabrowski"),
    )

    private class Text(private val en: String, private val uk: String, private val pl: String) {
        fun of(language: Language): String {
            return when (language) {
                Language.EN -> en
                Language.UK -> uk
                Language.PL -> pl
            }
        }
    }

    private class KindTemplate(val name: Text, val groups: List<GroupTemplate>)

    private class GroupTemplate(val name: Text, val services: List<ServiceTemplate>)

    private class ServiceTemplate(val name: Text, val duration: Duration, val priceInEuro: Int)
}

internal class SampleBusiness(
    val name: String,
    val currency: SampleCurrency,
    val groups: List<SampleGroup>,
    private val clients: List<SampleClient>,
) {
    fun clients(): List<Client.Detached> {
        return clients.map { client ->
            Client.Detached(
                id = Uuid.random(),
                name = client.name,
                lastName = client.lastName,
                phone = null,
                email = "${client.emailName}@example.com",
            )
        }
    }
}

internal class SampleClient(val name: String, val lastName: String, val emailName: String)

internal class SampleGroup(val name: String, val services: List<SampleService>)

internal class SampleService(val name: String, val duration: Duration, val priceInEuro: Int)

internal class SampleCurrency(val unit: CurrencyUnit, private val perEuro: Int) {
    fun priceOf(service: SampleService): Money {
        return Money.of(unit, (service.priceInEuro * perEuro).toBigDecimal())
    }
}
