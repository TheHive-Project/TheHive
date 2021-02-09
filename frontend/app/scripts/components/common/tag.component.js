(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('tag', {
            controller: function() {
                this.$onInit = function() {
                    if(!this.value) {
                        return;
                    }                    
                    if(_.isString(this.value)) {
                        this.tag = this.value;
                        this.bgColor = '#3c8dbc';
                    } else {
                        this.tag = _.without([
                            this.value.namespace,
                            ':',
                            this.value.predicate,
                            this.value.value ? ("=\"" + this.value.value + "\"") : null
                        ], null).join('');
                        this.bgColor = this.value.colour || '#3c8dbc';
                    }
                };
            },
            controllerAs: '$ctrl',
            replace: true,
            templateUrl: 'views/components/common/tag.component.html',
            bindings: {
                value: '<'
            }
        });
})();
