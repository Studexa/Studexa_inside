package com.studexa.ai;

import java.util.List;

import com.studexa.common.ApiException;

public record StudySample(String id, String language, String title, String text, List<Prompt> prompts) {

    static final List<StudySample> CATALOG = List.of(
            new StudySample("water-cycle-en", "en", "The water cycle",
                    """
                    The Sun warms liquid water in oceans, lakes, and rivers. Some of it evaporates into water
                    vapor, a gas. Plants also release water vapor through a process called transpiration.
                    When water vapor cools, it condenses into tiny liquid droplets that form clouds.
                    When droplets or ice crystals grow heavy enough, they fall as precipitation, such as
                    rain or snow. Some water runs over the ground into rivers and oceans, while some
                    soaks into the soil and becomes groundwater.
                    """.strip(),
                    List.of(new Prompt("explain", "Explain how clouds form."),
                            new Prompt("compare", "How are evaporation and condensation different?"),
                            new Prompt("practice", "Give me a practice question about precipitation."))),
            new StudySample("water-cycle-km", "km", "វដ្តទឹក",
                    """
                    ព្រះអាទិត្យកម្តៅទឹកក្នុងមហាសមុទ្រ បឹង និងទន្លេ។ ទឹកមួយចំនួនហួតក្លាយជាចំហាយទឹក។
                    រុក្ខជាតិក៏បញ្ចេញចំហាយទឹកតាមស្លឹកផងដែរ។ នៅពេលចំហាយទឹកត្រជាក់ វាកកជាដំណក់ទឹកតូចៗ
                    ដែលបង្កើតជាពពក។ នៅពេលដំណក់ទឹក ឬគ្រាប់ទឹកកកធ្ងន់ល្មម វាធ្លាក់មកដីជាភ្លៀង
                    ឬព្រិល។ ទឹកខ្លះហូរតាមផ្ទៃដីចូលទន្លេ និងមហាសមុទ្រ ឯទឹកខ្លះជ្រាបចូលដី
                    ហើយក្លាយជាទឹកក្រោមដី។
                    """.strip(),
                    List.of(new Prompt("explain", "សូមពន្យល់ពីរបៀបដែលពពកកើតឡើង។"),
                            new Prompt("compare", "តើការហួត និងការកកជាដំណក់ទឹកខុសគ្នាយ៉ាងដូចម្តេច?"),
                            new Prompt("practice", "សូមផ្តល់សំណួរអនុវត្តមួយអំពីភ្លៀង។"))));

    static StudySample find(String id) {
        return CATALOG.stream().filter(sample -> sample.id().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound("This study sample is unavailable."));
    }

    String question(String promptId) {
        return prompts.stream().filter(prompt -> prompt.id().equals(promptId))
                .findFirst().map(Prompt::question)
                .orElseThrow(() -> ApiException.badRequest("AI_UNKNOWN_PROMPT", "Choose a provided prompt."));
    }

    public record Prompt(String id, String question) {}
}
