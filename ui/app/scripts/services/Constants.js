(function() {
    'use strict';
    angular.module('theHiveServices')
        .value('CaseResolutionStatus', {
            Indeterminate: 'Indeterminate',
            FalsePositive: 'False Positive',
            TruePositive: 'True Positive',
            Duplicated: 'Duplicate',
            Other: 'Other'
        })
        .value('CaseImpactStatus', {
            NoImpact: 'No Impact',
            WithImpact: 'With impact',
            NotApplicable: 'Not Applicable'
        })
        .value('Severity', {
            keys: {
                High: 3,
                Medium: 2,
                Low: 1
            },
            values: ['Unknown', 'Low', 'Medium', 'High']
        })
        .value('Tlp', {
            RED: 3,
            AMBER: 2,
            GREEN: 1,
            WHITE: 1
        });
})();
