(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('auditDatalist', {
            controller: function() {

                // this.updateField = function(newValue) {
                //     this.onUpdate({
                //         fieldName: ['customFields', this.field.reference, this.field.type].join('.'),
                //         value: newValue
                //     });
                // };

            },
            controllerAs: '$cmp',
            templateUrl: 'views/components/datalist/audit.datalist.component.html',
            bindings: {
                data: '<'
            }
        });
})();
