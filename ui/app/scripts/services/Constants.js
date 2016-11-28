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
        });
})();
