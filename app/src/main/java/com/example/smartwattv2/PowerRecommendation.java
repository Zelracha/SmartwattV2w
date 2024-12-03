package com.example.smartwattv2;

import java.util.ArrayList;
import java.util.List;

public class PowerRecommendation {

    public static class Recommendation {
        public String title;
        public String description;
        public String category;

        public Recommendation(String title, String description, String category) {
            this.title = title;
            this.description = description;
            this.category = category;
        }
    }

    public static List<Recommendation> getGeneralRecommendations() {
        List<Recommendation> recommendations = new ArrayList<>();

        // Lighting Recommendations
        recommendations.add(new Recommendation(
                "Switch to LED Bulbs",
                "Replace traditional bulbs with LED lights to save up to 80% energy on lighting.",
                "Lighting"
        ));
        recommendations.add(new Recommendation(
                "Natural Light Usage",
                "Maximize natural daylight and turn off lights in unused rooms.",
                "Lighting"
        ));

        // Appliance Usage
        recommendations.add(new Recommendation(
                "Efficient Appliance Usage",
                "Use energy-efficient appliances and run them during off-peak hours.",
                "Appliances"
        ));
        recommendations.add(new Recommendation(
                "Regular Maintenance",
                "Clean or replace air filters and maintain appliances regularly for optimal efficiency.",
                "Appliances"
        ));

        // Temperature Control
        recommendations.add(new Recommendation(
                "Temperature Management",
                "Set air conditioner to 24-26°C for optimal energy efficiency.",
                "Temperature"
        ));
        recommendations.add(new Recommendation(
                "Natural Ventilation",
                "Use natural ventilation when possible instead of air conditioning.",
                "Temperature"
        ));

        return recommendations;
    }

    public static List<Recommendation> getConsumptionBasedRecommendations(float currentKwh, float limit) {
        List<Recommendation> recommendations = new ArrayList<>();

        float usagePercentage = (currentKwh / limit) * 100;

        if (usagePercentage > 90) {
            recommendations.add(new Recommendation(
                    "Critical Usage Alert",
                    "You've used " + String.format("%.1f", usagePercentage) + "% of your limit. Consider immediate action to reduce consumption.",
                    "Alert"
            ));
            recommendations.add(new Recommendation(
                    "Immediate Actions",
                    "• Turn off non-essential appliances\n" +
                            "• Check for energy-intensive devices\n" +
                            "• Use natural lighting where possible\n" +
                            "• Postpone high-power activities",
                    "Action"
            ));
        } else if (usagePercentage > 75) {
            recommendations.add(new Recommendation(
                    "High Usage Warning",
                    "You're at " + String.format("%.1f", usagePercentage) + "% of your limit. Consider reducing consumption.",
                    "Warning"
            ));
            recommendations.add(new Recommendation(
                    "Recommended Actions",
                    "• Review appliance usage\n" +
                            "• Use energy-saving modes\n" +
                            "• Optimize temperature settings",
                    "Action"
            ));
        } else if (usagePercentage > 50) {
            recommendations.add(new Recommendation(
                    "Moderate Usage Notice",
                    "You're at " + String.format("%.1f", usagePercentage) + "% of your limit. Monitor your usage.",
                    "Notice"
            ));
            recommendations.add(new Recommendation(
                    "Preventive Actions",
                    "• Monitor major appliance usage\n" +
                            "• Consider energy-saving practices\n" +
                            "• Plan usage of high-power devices",
                    "Action"
            ));
        }

        // Add consumption-specific recommendations
        if (currentKwh > 5) {
            recommendations.add(new Recommendation(
                    "High Consumption Pattern",
                    "Your consumption suggests heavy appliance usage. Consider:\n" +
                            "• Using appliances during off-peak hours\n" +
                            "• Checking for energy-intensive devices\n" +
                            "• Reviewing air conditioning settings",
                    "Usage Pattern"
            ));
        }

        return recommendations;
    }

    public static String getSuggestedLimit(float averageUsage) {
        if (averageUsage < 2) {
            return "Suggested limit: 3 kWh (Suitable for small apartments with minimal appliance usage)";
        } else if (averageUsage < 5) {
            return "Suggested limit: 5 kWh (Suitable for medium-sized homes with moderate appliance usage)";
        } else if (averageUsage < 8) {
            return "Suggested limit: 8 kWh (Suitable for larger homes with regular appliance usage)";
        } else {
            return "Suggested limit: 10 kWh (Suitable for large homes with heavy appliance usage)";
        }
    }
}