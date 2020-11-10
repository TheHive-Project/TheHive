(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('customFieldLabels', {
            controller: function(CustomFieldsSrv) {
                var self = this;

                this.fieldsCache = {};
                this.fieldClicked = function(fieldName, newValue) {
                    this.onFieldClick({
                        name: fieldName,
                        value: newValue
                    });
                };

                this.$onInit = function() {
                    CustomFieldsSrv.all().then(function(fields) {
                        self.fieldsCache = fields;
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
