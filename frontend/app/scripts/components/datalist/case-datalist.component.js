(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('caseDatalist', {
            controller: function() {

                // this.updateField = function(newValue) {
                //     this.onUpdate({
                //         fieldName: ['customFields', this.field.reference, this.field.type].join('.'),
                //         value: newValue
                //     });
                // };

            },
            controllerAs: '$cmp',
            templateUrl: 'views/components/datalist/case.datalist.component.html',
            bindings: {
                data: '<'
            }
        });
})();
