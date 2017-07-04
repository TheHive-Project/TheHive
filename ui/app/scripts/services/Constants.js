(function() {
    'use strict';
    angular.module('theHiveServices')
        .value('duScrollOffset', 30)
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
        .value('AlertStatus', {
            values: ['New', 'Updated', 'Ignored', 'Imported']
        })
        .value('Tlp', {
            keys: {
                Red: 3,
                Amber: 2,
                Green: 1,
                White: 0
            },
            values: ['White', 'Green', 'Amber', 'Red']
        });
})();
