(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('customFieldLabels', {
            controller: function() {
                this.fieldClicked = function(fieldName, newValue) {
                    this.onFieldClick({
                        name: fieldName,
                        value: newValue
                    });
                };
            },
            controllerAs: '$cmp',
            templateUrl: 'views/components/common/custom-field-labels.component.html',
            bindings: {
                customFields: '<',
                onFieldClick: '&'
            }
        });
})();
